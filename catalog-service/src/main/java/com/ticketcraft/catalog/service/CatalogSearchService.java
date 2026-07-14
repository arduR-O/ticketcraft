package com.ticketcraft.catalog.service;

import com.ticketcraft.catalog.model.Event;
import com.ticketcraft.catalog.model.Seat;
import com.ticketcraft.catalog.repository.EventRepository;
import com.ticketcraft.catalog.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogSearchService {

    private final EventRepository eventRepository;
    private final SeatRepository seatRepository;

    /**
     * Search events using PostgreSQL native full-text search against 'search_vector'.
     */
    public List<Event> searchEvents(String query) {
        if (query == null || query.trim().isEmpty()) {
            return eventRepository.findAll();
        }
        return eventRepository.searchEvents(query);
    }

    /**
     * Get all seats for a specific event.
     */
    public List<Seat> getSeatsForEvent(Long eventId) {
        return seatRepository.findByEventId(eventId);
    }
}
