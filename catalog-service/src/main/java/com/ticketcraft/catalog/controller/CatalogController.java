package com.ticketcraft.catalog.controller;

import com.ticketcraft.catalog.model.Event;
import com.ticketcraft.catalog.model.Seat;
import com.ticketcraft.catalog.service.CatalogSearchService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class CatalogController {

  private final CatalogSearchService catalogSearchService;

  /** Search events based on partial/full FTS keywords. */
  @GetMapping("/search")
  public ResponseEntity<List<Event>> searchEvents(
      @RequestParam(value = "query", required = false) String query) {
    List<Event> events = catalogSearchService.searchEvents(query);
    return ResponseEntity.ok(events);
  }

  /** Fetch the seat map list for a specific event. */
  @GetMapping("/{id}/seatmap")
  public ResponseEntity<List<Seat>> getSeatmap(@PathVariable("id") Long id) {
    List<Seat> seats = catalogSearchService.getSeatsForEvent(id);
    return ResponseEntity.ok(seats);
  }
}
