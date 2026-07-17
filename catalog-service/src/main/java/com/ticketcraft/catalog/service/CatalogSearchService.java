package com.ticketcraft.catalog.service;

import com.ticketcraft.catalog.dto.EventResponse;
import com.ticketcraft.catalog.dto.SeatResponse;
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
    if (!eventRepository.existsById(eventId)) {
      throw new com.ticketcraft.catalog.exception.EventNotFoundException(
          "Event not found with ID: " + eventId);
    }
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

  public com.ticketcraft.catalog.dto.EventDetailResponse getEventDetail(Long eventId) {
    com.ticketcraft.catalog.model.Event event =
        eventRepository
            .findById(eventId)
            .orElseThrow(
                () ->
                    new com.ticketcraft.catalog.exception.EventNotFoundException(
                        "Event not found with ID: " + eventId));
    List<com.ticketcraft.catalog.model.Seat> seats = seatRepository.findByEventId(eventId);

    long totalSeats = seats.size();
    long availableSeats =
        seats.stream()
            .filter(s -> s.getStatus() == com.ticketcraft.catalog.model.SeatStatus.AVAILABLE)
            .count();

    java.util.Map<String, java.math.BigDecimal> pricingTiers = new java.util.HashMap<>();
    for (com.ticketcraft.catalog.model.Seat seat : seats) {
      pricingTiers.put(seat.getCategory().name(), seat.getPrice());
    }

    return new com.ticketcraft.catalog.dto.EventDetailResponse(
        event.getId(),
        event.getTitle(),
        event.getDescription(),
        event.getDate(),
        event.getArtist().getName(),
        event.getVenue().getName(),
        event.getVenue().getLocation(),
        event.getVenue().getLatitude(),
        event.getVenue().getLongitude(),
        totalSeats,
        availableSeats,
        pricingTiers);
  }
}
