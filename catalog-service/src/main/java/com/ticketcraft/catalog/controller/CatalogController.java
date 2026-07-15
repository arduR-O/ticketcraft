package com.ticketcraft.catalog.controller;

import com.ticketcraft.catalog.model.Event;
import com.ticketcraft.catalog.model.Seat;
import com.ticketcraft.catalog.service.CatalogSearchService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class CatalogController {

  private final CatalogSearchService catalogSearchService;

  private static final int PAGE_SIZE = 20;

  @GetMapping("/search")
  public ResponseEntity<List<Event>> searchEvents(
      @RequestParam(value = "query", required = false) String query,
      @RequestParam(value = "page", defaultValue = "0") int page) {
    Pageable pageable = PageRequest.of(page, PAGE_SIZE);
    List<Event> events = catalogSearchService.searchEvents(query, pageable);
    return ResponseEntity.ok(events);
  }

  @GetMapping("/{id}/seatmap")
  public ResponseEntity<List<Seat>> getSeatmap(@PathVariable("id") Long id) {
    List<Seat> seats = catalogSearchService.getSeatsForEvent(id);
    return ResponseEntity.ok(seats);
  }
}
