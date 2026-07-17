package com.ticketcraft.booking.service;

import com.ticketcraft.booking.dto.BookingRequest;
import com.ticketcraft.booking.dto.BookingResponse;
import com.ticketcraft.booking.dto.CheckoutRequest;
import com.ticketcraft.booking.dto.SeatResponse;
import com.ticketcraft.booking.event.BookingCreatedEvent;
import com.ticketcraft.booking.event.SeatStatusChangedEvent;
import com.ticketcraft.booking.exception.InvalidBookingRequestException;
import com.ticketcraft.booking.exception.SeatUnavailableException;
import com.ticketcraft.booking.grpc.CatalogGrpcClient;
import com.ticketcraft.booking.kafka.BookingCreatedProducer;
import com.ticketcraft.booking.kafka.SeatStatusProducer;
import com.ticketcraft.booking.model.Booking;
import com.ticketcraft.booking.model.BookingStatus;
import com.ticketcraft.booking.model.ReservedSeat;
import com.ticketcraft.booking.repository.BookingRepository;
import com.ticketcraft.catalog.grpc.SeatCheckResponse;
import com.ticketcraft.catalog.grpc.SeatInfo;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

  private final BookingRepository bookingRepository;
  private final SeatLockService seatLockService;
  private final CatalogGrpcClient catalogGrpcClient;
  private final SeatStatusProducer seatStatusProducer;
  private final BookingCreatedProducer bookingCreatedProducer;
  private final PaymentTokenizer paymentTokenizer;

  @Value("${booking.cart-expiry-minutes:10}")
  private int cartExpiryMinutes;

  @Value("${booking.max-seats-per-booking:6}")
  private int maxSeatsPerBooking;

  @Transactional
  public BookingResponse reserveSeats(BookingRequest request, String userId) {
    if (request.getSeatIds() == null || request.getSeatIds().isEmpty()) {
      throw new InvalidBookingRequestException("At least one seat must be selected");
    }
    if (request.getSeatIds().size() > maxSeatsPerBooking) {
      throw new InvalidBookingRequestException("Maximum " + maxSeatsPerBooking + " seats per booking");
    }

    // 1. Sort seat IDs to prevent deadlocks across concurrent requests
    List<Long> sortedSeatIds = new ArrayList<>(request.getSeatIds());
    Collections.sort(sortedSeatIds);

    // 2. Try acquiring distributed locks
    boolean locksAcquired = seatLockService.acquireLocks(sortedSeatIds);
    if (!locksAcquired) {
      throw new SeatUnavailableException("One or more seats are already locked or sold");
    }

    try {
      // 3. Check seat validity and prices via gRPC
      SeatCheckResponse checkSeatsResponse = catalogGrpcClient.checkSeats(request.getEventId(), sortedSeatIds);
      if (!checkSeatsResponse.getAllAvailable()) {
        throw new SeatUnavailableException("Seats unavailable according to catalog");
      }

      // 4. Calculate total price and prepare denormalized seat entities
      BigDecimal totalPrice = BigDecimal.ZERO;
      List<ReservedSeat> reservedSeats = new ArrayList<>();
      List<SeatResponse> seatResponses = new ArrayList<>();

      for (SeatInfo seatInfo : checkSeatsResponse.getSeatsList()) {
        BigDecimal price = new BigDecimal(seatInfo.getPrice());
        totalPrice = totalPrice.add(price);

        ReservedSeat seat = ReservedSeat.builder()
            .seatId(seatInfo.getSeatId())
            .seatNumber(seatInfo.getSeatNumber())
            .rowNumber(seatInfo.getRowNumber())
            .section(seatInfo.getSection())
            .price(price)
            .build();
        reservedSeats.add(seat);

        seatResponses.add(new SeatResponse(
            seatInfo.getSeatId(), seatInfo.getSeatNumber(), seatInfo.getRowNumber(), seatInfo.getSection(), price
        ));
      }

      // 5. Save Booking to Database
      LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
      LocalDateTime expiresAt = now.plusMinutes(cartExpiryMinutes);

      Booking booking = Booking.builder()
          .eventId(request.getEventId())
          .userId(userId)
          .status(BookingStatus.PENDING)
          .totalPrice(totalPrice)
          .createdAt(now)
          .expiresAt(expiresAt)
          .build();

      for (ReservedSeat seat : reservedSeats) {
        booking.addReservedSeat(seat);
      }

      Booking savedBooking = bookingRepository.save(booking);

      // 6. Publish SeatStatusChangedEvent to Kafka
      SeatStatusChangedEvent event = SeatStatusChangedEvent.builder()
          .eventId(request.getEventId())
          .seatIds(sortedSeatIds)
          .newStatus("LOCKED")
          .bookingId(savedBooking.getId().toString())
          .timestamp(Instant.now().toString())
          .build();
      seatStatusProducer.sendSeatStatusChanged(event);

      // 7. Return Response
      return BookingResponse.builder()
          .bookingId(savedBooking.getId())
          .eventId(savedBooking.getEventId())
          .status(savedBooking.getStatus())
          .seats(seatResponses)
          .totalPrice(savedBooking.getTotalPrice())
          .createdAt(savedBooking.getCreatedAt())
          .expiresAt(savedBooking.getExpiresAt())
          .build();

    } catch (Exception e) {
      // If any exception occurs (like gRPC failure or DB failure), release locks
      seatLockService.releaseLocksByIds(sortedSeatIds);
      throw e;
    }
  }

  @Transactional
  public void initiateCheckout(UUID bookingId, CheckoutRequest request, String userId) {
    Booking booking = bookingRepository.findById(bookingId)
        .orElseThrow(() -> new InvalidBookingRequestException("Booking not found"));

    if (!booking.getUserId().equals(userId)) {
      throw new InvalidBookingRequestException("Booking does not belong to user");
    }

    if (booking.getStatus() != BookingStatus.PENDING) {
      throw new InvalidBookingRequestException("Booking has already been processed or cancelled");
    }

    if (booking.getExpiresAt().isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
      throw new InvalidBookingRequestException("Booking has expired");
    }

    // Tokenize payment details (PCI boundary)
    String paymentToken = paymentTokenizer.tokenize(request.getCardNumber());

    // Publish BookingCreatedEvent to trigger payment processing
    BookingCreatedEvent event = BookingCreatedEvent.builder()
        .bookingId(bookingId.toString())
        .totalPrice(booking.getTotalPrice().toString())
        .paymentToken(paymentToken)
        .userId(userId)
        .eventId(booking.getEventId())
        .timestamp(Instant.now().toString())
        .build();
    
    bookingCreatedProducer.sendBookingCreated(event);
  }

  public BookingResponse getBookingStatus(UUID bookingId, String userId) {
    Booking booking = bookingRepository.findById(bookingId)
        .orElseThrow(() -> new InvalidBookingRequestException("Booking not found"));
    
    if (!booking.getUserId().equals(userId)) {
      throw new InvalidBookingRequestException("Booking does not belong to user");
    }

    List<SeatResponse> seatResponses = booking.getReservedSeats().stream()
        .map(seat -> new SeatResponse(
            seat.getSeatId(), seat.getSeatNumber(), seat.getRowNumber(), seat.getSection(), seat.getPrice()))
        .collect(Collectors.toList());

    return BookingResponse.builder()
        .bookingId(booking.getId())
        .eventId(booking.getEventId())
        .status(booking.getStatus())
        .seats(seatResponses)
        .totalPrice(booking.getTotalPrice())
        .createdAt(booking.getCreatedAt())
        .expiresAt(booking.getExpiresAt())
        .build();
  }
}
