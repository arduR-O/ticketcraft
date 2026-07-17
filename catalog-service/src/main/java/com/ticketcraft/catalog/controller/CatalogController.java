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

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@Validated
public class CatalogController {

  private final CatalogSearchService catalogSearchService;

  private static final int PAGE_SIZE = 20;

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

  @GetMapping("/{id}/seatmap")
  public ResponseEntity<List<SeatResponse>> getSeatmap(@PathVariable("id") Long id) {
    List<SeatResponse> seats = catalogSearchService.getSeatsForEvent(id);
    return ResponseEntity.ok(seats);
  }

  @GetMapping("/{id}")
  public ResponseEntity<com.ticketcraft.catalog.dto.EventDetailResponse> getEventDetail(
      @PathVariable("id") Long id) {
    com.ticketcraft.catalog.dto.EventDetailResponse eventDetail =
        catalogSearchService.getEventDetail(id);
    return ResponseEntity.ok(eventDetail);
  }
}
