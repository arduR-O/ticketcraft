package com.ticketcraft.booking.controller;

import com.ticketcraft.booking.dto.BookingRequest;
import com.ticketcraft.booking.dto.BookingResponse;
import com.ticketcraft.booking.dto.CheckoutRequest;
import com.ticketcraft.booking.service.BookingService;
import com.ticketcraft.booking.service.SseService;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

  private final BookingService bookingService;
  private final SseService sseService;

  @PostMapping
  public ResponseEntity<BookingResponse> reserveSeats(
      @RequestHeader(value = "X-Validated-UserId", required = false, defaultValue = "alice") String userId,
      @RequestBody BookingRequest request) {
    
    log.info("User {} requesting to reserve seats for event {}: {}", userId, request.getEventId(), request.getSeatIds());
    BookingResponse response = bookingService.reserveSeats(request, userId);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @PostMapping("/{bookingId}/checkout")
  public ResponseEntity<Map<String, String>> checkout(
      @RequestHeader(value = "X-Validated-UserId", required = false, defaultValue = "alice") String userId,
      @PathVariable UUID bookingId,
      @RequestBody CheckoutRequest request) {
    
    log.info("User {} initiating checkout for booking {}", userId, bookingId);
    bookingService.initiateCheckout(bookingId, request, userId);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
        "bookingId", bookingId.toString(),
        "status", "PROCESSING",
        "message", "Payment is being processed. You will receive confirmation shortly."
    ));
  }

  @GetMapping("/{bookingId}")
  public ResponseEntity<BookingResponse> getBookingStatus(
      @RequestHeader(value = "X-Validated-UserId", required = false, defaultValue = "alice") String userId,
      @PathVariable UUID bookingId) {
    
    BookingResponse response = bookingService.getBookingStatus(bookingId, userId);
    return ResponseEntity.ok(response);
  }

  @GetMapping("/{bookingId}/status-stream")
  public SseEmitter streamBookingStatus(
      @RequestHeader(value = "X-Validated-UserId", required = false, defaultValue = "alice") String userId,
      @PathVariable UUID bookingId) {
    
    // First, verify the booking belongs to the user
    bookingService.getBookingStatus(bookingId, userId);
    return sseService.subscribe(bookingId);
  }
}
