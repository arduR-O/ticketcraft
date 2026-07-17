package com.ticketcraft.booking.scheduler;

import com.ticketcraft.booking.event.SeatStatusChangedEvent;
import com.ticketcraft.booking.kafka.SeatStatusProducer;
import com.ticketcraft.booking.model.Booking;
import com.ticketcraft.booking.model.BookingStatus;
import com.ticketcraft.booking.model.ReservedSeat;
import com.ticketcraft.booking.repository.BookingRepository;
import com.ticketcraft.booking.repository.ReservedSeatRepository;
import com.ticketcraft.booking.service.SeatLockService;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingExpirationScheduler {

  private final BookingRepository bookingRepository;
  private final ReservedSeatRepository reservedSeatRepository;
  private final SeatLockService seatLockService;
  private final SeatStatusProducer seatStatusProducer;

  @Scheduled(fixedDelay = 60000) // Poll every 60 seconds
  @Transactional
  public void cleanExpiredCarts() {
    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
    List<Booking> expiredBookings = bookingRepository.findExpiredBookings(BookingStatus.PENDING, now);

    if (expiredBookings.isEmpty()) {
      return;
    }

    log.info("Found {} expired bookings to clean up", expiredBookings.size());

    for (Booking booking : expiredBookings) {
      try {
        // 1. Fetch seat IDs BEFORE any mutations
        List<Long> seatIds = booking.getReservedSeats().stream()
            .map(ReservedSeat::getSeatId)
            .collect(Collectors.toList());

        // 2. Release Redisson locks for each fetched seatId
        seatLockService.releaseLocksByIds(seatIds);

        // 3. Kafka PRODUCE -> "seat-status-changed"
        SeatStatusChangedEvent event = SeatStatusChangedEvent.builder()
            .eventId(booking.getEventId())
            .seatIds(seatIds)
            .newStatus("AVAILABLE")
            .bookingId(booking.getId().toString())
            .timestamp(Instant.now().toString())
            .build();
        seatStatusProducer.sendSeatStatusChanged(event);

        // 4. Update booking status
        booking.setStatus(BookingStatus.EXPIRED);
        booking.setUpdatedAt(now);
        bookingRepository.save(booking);

        // 5. Delete reserved seats
        reservedSeatRepository.deleteByBookingId(booking.getId());

        log.info("Successfully cleaned up expired booking {}", booking.getId());
      } catch (Exception e) {
        log.error("Failed to clean up expired booking {}", booking.getId(), e);
      }
    }
  }
}
