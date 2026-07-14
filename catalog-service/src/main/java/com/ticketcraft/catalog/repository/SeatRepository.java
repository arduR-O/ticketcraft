package com.ticketcraft.catalog.repository;

import com.ticketcraft.catalog.model.Seat;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Long> {
  List<Seat> findByEventId(Long eventId);
}
