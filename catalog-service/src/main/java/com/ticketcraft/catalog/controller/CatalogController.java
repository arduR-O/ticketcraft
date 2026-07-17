package com.ticketcraft.catalog.controller;

import com.ticketcraft.catalog.dto.EventResponse;
import com.ticketcraft.catalog.dto.SeatResponse;
import com.ticketcraft.catalog.service.CatalogSearchService;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller exposing read-only catalog endpoints for frontend clients.
 * 
 * What: Handles event search, seat map generation, and event details.
 * 
 * Why: Placed behind the Gateway but does not require strict authentication (public routes),
 * allowing unauthenticated users to browse events and see seat availability before logging in.
 */
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@Validated
public class CatalogController {

  private final CatalogSearchService catalogSearchService;

  private static final int PAGE_SIZE = 20;

  /**
   * Endpoint for paginated event search.
   * 
   * What: Accepts a search query and a page number, returning a list of events.
   * 
   * Why: Uses pagination to handle high traffic efficiently and avoid OutOfMemory errors
   * when the database contains thousands of events.
   * 
   * @param query The search text (optional).
   * @param page The page number (0-indexed).
   * @return A list of events on the given page.
   */
  @GetMapping("/search")
  public ResponseEntity<List<EventResponse>> searchEvents(
      @RequestParam(value = "query", required = false) String query,
      @RequestParam(value = "page", defaultValue = "0")
          @Min(value = 0, message = "Page number must be >= 0")
          int page) {
    Pageable pageable = PageRequest.of(page, PAGE_SIZE);
    List<EventResponse> events = catalogSearchService.searchEvents(query, pageable);
    return ResponseEntity.ok(events);
  }

  /**
   * Endpoint for fetching the initial seat map for an event.
   * 
   * What: Retrieves all seats associated with the given event ID.
   * 
   * Why: Clients call this once when loading the event page to build the initial SVG or Canvas
   * representation of the venue, then subscribe to the SSE stream for live updates.
   * 
   * @param id The event ID.
   * @return The list of seats.
   */
  @GetMapping("/{id}/seatmap")
  public ResponseEntity<List<SeatResponse>> getSeatmap(@PathVariable("id") Long id) {
    List<SeatResponse> seats = catalogSearchService.getSeatsForEvent(id);
    return ResponseEntity.ok(seats);
  }

  /**
   * Endpoint for fetching aggregated event details.
   * 
   * What: Retrieves the event metadata along with aggregated seat counts and pricing tiers.
   * 
   * Why: Prevents the client from needing to download the entire seatmap just to render the
   * summary page (price range, availability).
   * 
   * @param id The event ID.
   * @return The EventDetailResponse payload.
   */
  @GetMapping("/{id}")
  public ResponseEntity<com.ticketcraft.catalog.dto.EventDetailResponse> getEventDetail(
      @PathVariable("id") Long id) {
    com.ticketcraft.catalog.dto.EventDetailResponse eventDetail =
        catalogSearchService.getEventDetail(id);
    return ResponseEntity.ok(eventDetail);
  }
}
