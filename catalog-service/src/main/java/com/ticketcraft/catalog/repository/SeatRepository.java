package com.ticketcraft.catalog.repository;

import com.ticketcraft.catalog.model.Seat;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Long> {
  List<Seat> findByEventId(Long eventId);

  @Query("SELECT s FROM Seat s JOIN FETCH s.event WHERE s.id IN :ids")
  List<Seat> findAllByIdWithEvent(@Param("ids") Iterable<Long> ids);
}
