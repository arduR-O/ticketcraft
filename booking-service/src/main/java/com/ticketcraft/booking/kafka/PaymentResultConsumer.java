package com.ticketcraft.booking.kafka;

import com.ticketcraft.booking.event.PaymentProcessedEvent;
import com.ticketcraft.booking.event.SeatStatusChangedEvent;
import com.ticketcraft.booking.model.Booking;
import com.ticketcraft.booking.model.BookingStatus;
import com.ticketcraft.booking.model.ReservedSeat;
import com.ticketcraft.booking.repository.BookingRepository;
import com.ticketcraft.booking.repository.ReservedSeatRepository;
import com.ticketcraft.booking.service.SeatLockService;
import com.ticketcraft.booking.service.SseService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentResultConsumer {

  private final BookingRepository bookingRepository;
  private final ReservedSeatRepository reservedSeatRepository;
  private final SeatLockService seatLockService;
  private final SeatStatusProducer seatStatusProducer;
  private final SseService sseService;

  @KafkaListener(topics = "payment-processed", groupId = "booking-payment-consumer")
  @Transactional
  public void consumePaymentProcessed(PaymentProcessedEvent event) {
    log.info("Received PaymentProcessedEvent: {}", event);

    UUID bookingId;
    try {
      bookingId = UUID.fromString(event.getBookingId());
    } catch (IllegalArgumentException e) {
      log.error("Invalid booking ID format in event: {}", event.getBookingId());
      return;
    }

    Optional<Booking> bookingOpt = bookingRepository.findById(bookingId);
    if (bookingOpt.isEmpty()) {
      log.warn("Booking {} not found for PaymentProcessedEvent. Might have expired or never existed.", bookingId);
      return;
    }

    Booking booking = bookingOpt.get();

    // Idempotency guard
    if (booking.getStatus() == BookingStatus.CONFIRMED || booking.getStatus() == BookingStatus.CANCELLED) {
      log.info("Skipping duplicate event for booking {}. Current status: {}", bookingId, booking.getStatus());
      return;
    }

    List<Long> seatIds = booking.getReservedSeats().stream()
        .map(ReservedSeat::getSeatId)
        .collect(Collectors.toList());

    if ("SUCCESS".equals(event.getStatus())) {
      // Payment Successful
      booking.setStatus(BookingStatus.CONFIRMED);
      booking.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
      bookingRepository.save(booking);

      seatLockService.releaseLocksByIds(seatIds);

      SeatStatusChangedEvent statusEvent = SeatStatusChangedEvent.builder()
          .eventId(booking.getEventId())
          .seatIds(seatIds)
          .newStatus("SOLD")
          .bookingId(bookingId.toString())
          .timestamp(Instant.now().toString())
          .build();
      seatStatusProducer.sendSeatStatusChanged(statusEvent);

      sseService.pushUpdate(bookingId, Map.of(
          "bookingId", bookingId.toString(),
          "status", "CONFIRMED",
          "transactionId", event.getTransactionId() != null ? event.getTransactionId() : "txn_unknown"
      ));
      sseService.complete(bookingId);

    } else {
      // Payment Failed
      booking.setStatus(BookingStatus.CANCELLED);
      booking.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
      bookingRepository.save(booking);

      reservedSeatRepository.deleteByBookingId(bookingId);
      
      seatLockService.releaseLocksByIds(seatIds);

      SeatStatusChangedEvent statusEvent = SeatStatusChangedEvent.builder()
          .eventId(booking.getEventId())
          .seatIds(seatIds)
          .newStatus("AVAILABLE")
          .bookingId(bookingId.toString())
          .timestamp(Instant.now().toString())
          .build();
      seatStatusProducer.sendSeatStatusChanged(statusEvent);

      sseService.pushUpdate(bookingId, Map.of(
          "bookingId", bookingId.toString(),
          "status", "CANCELLED",
          "reason", event.getFailureReason() != null ? event.getFailureReason() : "Payment failed"
      ));
      sseService.complete(bookingId);
    }
  }
}
