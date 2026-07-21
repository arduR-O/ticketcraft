package com.ticketcraft.booking.service;

import com.ticketcraft.booking.client.PaymentClient;
import com.ticketcraft.booking.dto.BookingRequest;
import com.ticketcraft.booking.dto.BookingResponse;
import com.ticketcraft.booking.dto.CheckoutRequest;
import com.ticketcraft.booking.dto.PaymentRequest;
import com.ticketcraft.booking.dto.PaymentResponse;
import com.ticketcraft.booking.dto.SeatResponse;
import com.ticketcraft.booking.event.SeatStatusChangedEvent;
import com.ticketcraft.booking.exception.InvalidBookingRequestException;
import com.ticketcraft.booking.exception.SeatUnavailableException;
import com.ticketcraft.booking.grpc.CatalogGrpcClient;
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

/**
 * Service responsible for orchestrating the booking and checkout flows.
 * It manages the lifecycle of a booking from reserving seats to finalizing payments.
 * 
 * This service acts as the central coordinator in the distributed architecture,
 * ensuring that distributed locks are acquired before relying on the catalog service,
 * and broadcasting state changes to Kafka so that downstream consumers (like the SSE stream)
 * can remain synchronized without needing direct database polling.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

  private final BookingRepository bookingRepository;
  private final SeatLockService seatLockService;
  private final CatalogGrpcClient catalogGrpcClient;
  private final SeatStatusProducer seatStatusProducer;
  private final PaymentClient paymentClient;
  private final PaymentTokenizer paymentTokenizer;

  @Value("${booking.cart-expiry-minutes:10}")
  private int cartExpiryMinutes;

  @Value("${booking.max-seats-per-booking:6}")
  private int maxSeatsPerBooking;

  /**
   * Reserves a list of seats for a user, acquiring distributed locks and verifying availability via gRPC.
   * 
   * Validates the request, acquires Redisson locks for the requested seats, validates availability
   * with the Catalog service via gRPC, and saves a PENDING booking. Finally, it broadcasts a LOCKED event.
   * 
   * We sort seat IDs before locking to prevent distributed deadlocks if multiple users try to book
   * overlapping seats concurrently. We use synchronous gRPC to the Catalog service to guarantee strong
   * consistency before writing to our local database. Kafka is used to fan-out the LOCKED state so the
   * frontend seatmap updates in real-time.
   *
   * @param request The booking request containing eventId and seatIds.
   * @param userId The ID of the user attempting to reserve seats.
   * @return A BookingResponse reflecting the pending cart state.
   */
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
      throw new SeatUnavailableException("One or more seats are currently locked by another user");
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
      return mapToBookingResponse(savedBooking);

    } catch (Exception e) {
      // If any exception occurs (like gRPC failure or DB failure), release locks
      seatLockService.releaseLocksByIds(sortedSeatIds);
      throw e;
    }
  }

  /**
   * Processes the checkout for a pending booking by invoking the payment service synchronously.
   * 
   * Validates the booking state and expiry, tokenizes the payment, and makes a synchronous HTTP
   * call to the Payment service. Depending on the result, it transitions the booking to CONFIRMED or
   * CANCELLED, releases the distributed locks, and broadcasts the final status (SOLD or AVAILABLE) to Kafka.
   * 
   * Checkout is implemented as a synchronous REST call rather than an async Kafka flow because
   * payment is a 1-to-1 short wait interaction from the user's perspective. It reduces architectural
   * overhead. Releasing locks immediately upon success/failure ensures we don't accidentally block
   * inventory for longer than necessary.
   *
   * @param bookingId The UUID of the pending booking.
   * @param request The checkout request containing payment details.
   * @param userId The ID of the user requesting checkout.
   * @return The finalized BookingResponse.
   */
  @Transactional
  public BookingResponse initiateCheckout(UUID bookingId, CheckoutRequest request, String userId) {
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

    // Make synchronous call to Payment Service
    PaymentResponse paymentResponse;
    try {
      paymentResponse = paymentClient.processPayment(PaymentRequest.builder()
          .bookingId(bookingId)
          .amount(booking.getTotalPrice())
          .paymentToken(paymentToken)
          .build());
    } catch (Exception e) {
      // Treat network errors/500s as a failure for now
      log.error("Payment service call failed for booking {}", bookingId, e);
      paymentResponse = PaymentResponse.builder()
          .status("FAILED")
          .failureReason("Payment service unavailable")
          .build();
    }

    List<Long> seatIds = booking.getReservedSeats().stream()
        .map(ReservedSeat::getSeatId)
        .collect(Collectors.toList());

    if ("SUCCESS".equals(paymentResponse.getStatus())) {
      booking.setStatus(BookingStatus.CONFIRMED);
      booking.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
      
      // Release Redisson locks
      seatLockService.releaseLocksByIds(seatIds);
      
      // Publish to Kafka: seat-status-changed (SOLD)
      SeatStatusChangedEvent event = SeatStatusChangedEvent.builder()
          .eventId(booking.getEventId())
          .seatIds(seatIds)
          .newStatus("SOLD")
          .bookingId(bookingId.toString())
          .timestamp(Instant.now().toString())
          .build();
      seatStatusProducer.sendSeatStatusChanged(event);
      
      Booking savedBooking = bookingRepository.save(booking);
      return mapToBookingResponse(savedBooking);

    } else {
      booking.setStatus(BookingStatus.CANCELLED);
      booking.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
      
      // Delete reserved seats (orphan removal handles this when cleared)
      booking.getReservedSeats().clear();
      
      // Release Redisson locks
      seatLockService.releaseLocksByIds(seatIds);
      
      // Publish to Kafka: seat-status-changed (AVAILABLE)
      SeatStatusChangedEvent event = SeatStatusChangedEvent.builder()
          .eventId(booking.getEventId())
          .seatIds(seatIds)
          .newStatus("AVAILABLE")
          .bookingId(bookingId.toString())
          .timestamp(Instant.now().toString())
          .build();
      seatStatusProducer.sendSeatStatusChanged(event);
      
      bookingRepository.save(booking);
      
      throw new InvalidBookingRequestException("Payment Declined: " + paymentResponse.getFailureReason());
    }
  }

  /**
   * Fetches the current status of a booking.
   * 
   * Retrieves the booking from the repository and verifies user ownership.
   * 
   * Used by the client to poll or recover state if the connection drops during checkout,
   * ensuring users can always see their confirmed tickets.
   *
   * @param bookingId The UUID of the booking.
   * @param userId The ID of the user requesting the status.
   * @return The BookingResponse.
   */
  public BookingResponse getBookingStatus(UUID bookingId, String userId) {
    Booking booking = bookingRepository.findById(bookingId)
        .orElseThrow(() -> new InvalidBookingRequestException("Booking not found"));
    
    if (!booking.getUserId().equals(userId)) {
      throw new InvalidBookingRequestException("Booking does not belong to user");
    }

    return mapToBookingResponse(booking);
  }
  
  private BookingResponse mapToBookingResponse(Booking booking) {
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
