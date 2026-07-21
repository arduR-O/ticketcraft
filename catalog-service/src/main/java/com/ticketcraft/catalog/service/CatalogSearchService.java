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

/**
 * Read-only service for querying catalog data (events, seats, and pricing).
 * 
 * Provides search capabilities for events and retrieves seat maps and detailed event data.
 * 
 * Separates read-heavy operations from write-heavy operations (like seat status updates). 
 * By marking it @Transactional(readOnly = true), we allow the underlying ORM to optimize fetches
 * and avoid dirty checking, improving read throughput for the high-traffic catalog search.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogSearchService {

  private final EventRepository eventRepository;
  private final SeatRepository seatRepository;

  /**
   * Searches for events matching a query string.
   * 
   * Executes a full-text or LIKE search on the event repository and maps the results to DTOs.
   * 
   * Used by the frontend search bar. Pagination is passed down to the repository layer
   * to prevent loading thousands of events into application memory at once.
   * 
   * @param query The search term.
   * @param pageable Pagination and sorting configuration.
   * @return A list of matching EventResponse DTOs.
   */
  public List<EventResponse> searchEvents(String query, Pageable pageable) {
    if (query == null || query.trim().isEmpty()) {
      return List.of();
    }
    return eventRepository.searchEvents(query, pageable).stream()
        .map(
            projection ->
                new EventResponse(
                    projection.getId(),
                    projection.getTitle(),
                    projection.getDescription(),
                    projection.getDate(),
                    projection.getArtistName(),
                    projection.getVenueName(),
                    projection.getVenueLocation()))
        .toList();
  }

  /**
   * Searches for events ordered by proximity to a given location.
   * 
   * Executes a native query with the Haversine formula to sort by distance.
   * 
   * Used by the frontend homepage to show events near the user's physical location.
   * 
   * @param lat The user's latitude.
   * @param lng The user's longitude.
   * @param pageable Pagination and sorting configuration.
   * @return A list of matching EventResponse DTOs ordered by distance.
   */
  public List<EventResponse> findNearbyEvents(double lat, double lng, Pageable pageable) {
    return eventRepository.findNearbyEvents(lat, lng, pageable).stream()
        .map(
            projection ->
                new EventResponse(
                    projection.getId(),
                    projection.getTitle(),
                    projection.getDescription(),
                    projection.getDate(),
                    projection.getArtistName(),
                    projection.getVenueName(),
                    projection.getVenueLocation()))
        .toList();
  }

  /**
   * Retrieves the full list of seats for a specific event.
   * 
   * Validates the event exists, fetches all seats linked to it, and maps them to SeatResponse DTOs.
   * 
   * Serves as the initial payload for the frontend seatmap rendering. Clients fetch this
   * once on page load, and subsequently rely on the SSE stream to receive delta updates.
   * 
   * @param eventId The ID of the event.
   * @return A list of all seats.
   */
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

  /**
   * Gets aggregated details for an event including pricing tiers and availability.
   * 
   * Fetches the event and its seats, aggregates the total/available seat counts, and builds
   * a distinct list of pricing tiers.
   * 
   * This provides the summary view for an event page (e.g. "From $50.00 | 200 seats left")
   * without requiring the client to manually fetch and compute thousands of seat records.
   * 
   * @param eventId The ID of the event.
   * @return The EventDetailResponse DTO.
   */
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
