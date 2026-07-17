package com.ticketcraft.booking.repository;

import com.ticketcraft.booking.model.ReservedSeat;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservedSeatRepository extends JpaRepository<ReservedSeat, Long> {
  void deleteByBookingId(UUID bookingId);
}
