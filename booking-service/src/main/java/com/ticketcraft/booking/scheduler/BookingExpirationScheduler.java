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

/**
 * Scheduled background task for cleaning up expired booking carts.
 * 
 * Runs on a fixed delay to sweep the database for PENDING bookings that have
 * passed their expiration time, returning those seats to the available pool.
 * 
 * When users reserve seats, we place a distributed lock on them and remove them
 * from availability. If the user abandons their cart or their browser crashes without
 * cancelling, we need an automated way to reclaim those seats so they aren't lost
 * forever. This scheduler acts as the garbage collector for stale carts.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingExpirationScheduler {

  private final BookingRepository bookingRepository;
  private final ReservedSeatRepository reservedSeatRepository;
  private final SeatLockService seatLockService;
  private final SeatStatusProducer seatStatusProducer;

  /**
   * Scans for and processes expired bookings.
   * 
   * Finds all PENDING bookings where `expiresAt` is in the past. For each, it:
   * 1) Releases the Redisson seat locks.
   * 2) Broadcasts a seat-status-changed (AVAILABLE) event to Kafka.
   * 3) Updates the booking status to EXPIRED.
   * 4) Deletes the reserved seat records.
   * 
   * This uses a pessimistic polling approach (every 60s) rather than relying
   * on delayed message queues. It is simpler to implement and guarantees eventual
   * consistency. We release locks with `forceUnlock()` because this background thread
   * did not originally acquire them. Kafka is used to fan-out the availability to
   * all connected frontend clients so they instantly see the seats free up.
   */
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
