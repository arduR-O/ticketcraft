package com.ticketcraft.booking.controller;

import com.ticketcraft.booking.dto.BookingRequest;
import com.ticketcraft.booking.dto.BookingResponse;
import com.ticketcraft.booking.dto.CheckoutRequest;
import com.ticketcraft.booking.service.BookingService;
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

@Slf4j
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

  private final BookingService bookingService;

  @PostMapping
  public ResponseEntity<BookingResponse> reserveSeats(
      @RequestHeader(value = "X-Validated-UserId", required = false, defaultValue = "alice") String userId,
      @RequestBody BookingRequest request) {
    
    log.info("User {} requesting to reserve seats for event {}: {}", userId, request.getEventId(), request.getSeatIds());
    BookingResponse response = bookingService.reserveSeats(request, userId);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @PostMapping("/{bookingId}/checkout")
  public ResponseEntity<BookingResponse> checkout(
      @RequestHeader(value = "X-Validated-UserId", required = false, defaultValue = "alice") String userId,
      @PathVariable UUID bookingId,
      @RequestBody CheckoutRequest request) {
    
    log.info("User {} initiating checkout for booking {}", userId, bookingId);
    BookingResponse response = bookingService.initiateCheckout(bookingId, request, userId);
    return ResponseEntity.ok(response);
  }

  @GetMapping("/{bookingId}")
  public ResponseEntity<BookingResponse> getBookingStatus(
      @RequestHeader(value = "X-Validated-UserId", required = false, defaultValue = "alice") String userId,
      @PathVariable UUID bookingId) {
    
    BookingResponse response = bookingService.getBookingStatus(bookingId, userId);
    return ResponseEntity.ok(response);
  }
}
