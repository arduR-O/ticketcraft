package com.ticketcraft.catalog.service;

import com.ticketcraft.catalog.dto.EventResponse;
import com.ticketcraft.catalog.dto.SeatResponse;
import com.ticketcraft.catalog.model.Event;
import com.ticketcraft.catalog.model.Seat;
import com.ticketcraft.catalog.repository.EventRepository;
import com.ticketcraft.catalog.repository.SeatRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogSearchService {

  private final EventRepository eventRepository;
  private final SeatRepository seatRepository;

  public List<EventResponse> searchEvents(String query, Pageable pageable) {
    if (query == null || query.trim().isEmpty()) {
      return List.of();
    }
    return eventRepository.searchEvents(query, pageable).stream()
        .map(
            event ->
                new EventResponse(
                    event.getId(),
                    event.getTitle(),
                    event.getDescription(),
                    event.getDate(),
                    event.getArtist().getName(),
                    event.getVenue().getName(),
                    event.getVenue().getLocation()))
        .toList();
  }

  public List<SeatResponse> getSeatsForEvent(Long eventId) {
    return seatRepository.findByEventId(eventId).stream()
        .map(
            seat ->
                new SeatResponse(
                    seat.getId(),
                    seat.getSeatNumber(),
                    seat.getRowNumber(),
                    seat.getSection(),
                    seat.getXCoordinate(),
                    seat.getYCoordinate(),
                    seat.getCategory(),
                    seat.getStatus(),
                    seat.getPrice(),
                    seat.getEvent().getId()))
        .toList();
  }
}
