package com.ticketcraft.catalog.service;

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

  public List<Event> searchEvents(String query, Pageable pageable) {
    if (query == null || query.trim().isEmpty()) {
      return eventRepository.findAll(pageable).getContent();
    }
    String formattedQuery = query.replaceAll("[^a-zA-Z0-9]", " ").trim().replaceAll("\\s+", " | ");

    if (formattedQuery.isEmpty()) {
      return eventRepository.findAll(pageable).getContent();
    }
    return eventRepository.searchEvents(formattedQuery, pageable);
  }

  public List<Seat> getSeatsForEvent(Long eventId) {
    return seatRepository.findByEventId(eventId);
  }
}
