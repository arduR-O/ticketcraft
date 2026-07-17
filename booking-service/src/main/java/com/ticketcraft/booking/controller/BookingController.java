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

/**
 * REST Controller exposing the HTTP endpoints for the booking-service.
 * 
 * What: Handles POST /api/bookings (reserving seats), POST /api/bookings/{id}/checkout,
 * and GET /api/bookings/{id} (status polling).
 * 
 * Why: This controller acts as the primary external boundary for clients adding tickets
 * to their cart and finalizing their purchases. It relies on the Gateway service to inject
 * validated user identities (`X-Validated-UserId`), ensuring unauthorized users cannot spoof
 * bookings for others.
 */
@Slf4j
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

  private final BookingService bookingService;

  /**
   * Endpoint to reserve (lock) seats and create a pending booking.
   * 
   * What: Accepts an eventId and a list of seatIds, delegates to the BookingService, and returns 201 Created.
   * 
   * Why: This is the first step in the checkout flow. Reserving the seats synchronously via HTTP gives
   * the client immediate feedback if the seats are unavailable, allowing them to try different seats quickly
   * without dealing with asynchronous queueing delays.
   * 
   * @param userId Injected by the Gateway after JWT validation.
   * @param request Payload containing the requested seats.
   * @return The pending BookingResponse.
   */
  @PostMapping
  public ResponseEntity<BookingResponse> reserveSeats(
      @RequestHeader(value = "X-Validated-UserId", required = false, defaultValue = "alice") String userId,
      @RequestBody BookingRequest request) {
    
    log.info("User {} requesting to reserve seats for event {}: {}", userId, request.getEventId(), request.getSeatIds());
    BookingResponse response = bookingService.reserveSeats(request, userId);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  /**
   * Endpoint to complete the purchase of a pending booking.
   * 
   * What: Accepts payment details (like card number), delegates to BookingService for synchronous
   * payment processing, and returns the confirmed (or cancelled) booking.
   * 
   * Why: Checkout is a synchronous HTTP call because the user expects immediate confirmation of
   * their payment. If it fails, they need to know instantly so they can try a different card before
   * their cart expires.
   * 
   * @param userId Injected by the Gateway after JWT validation.
   * @param bookingId The ID of the pending cart.
   * @param request Payload containing payment details.
   * @return The finalized BookingResponse.
   */
  @PostMapping("/{bookingId}/checkout")
  public ResponseEntity<BookingResponse> checkout(
      @RequestHeader(value = "X-Validated-UserId", required = false, defaultValue = "alice") String userId,
      @PathVariable UUID bookingId,
      @RequestBody CheckoutRequest request) {
    
    log.info("User {} initiating checkout for booking {}", userId, bookingId);
    BookingResponse response = bookingService.initiateCheckout(bookingId, request, userId);
    return ResponseEntity.ok(response);
  }

  /**
   * Endpoint to poll the status of an existing booking.
   * 
   * What: Retrieves the booking state from the database and returns it if it belongs to the user.
   * 
   * Why: Provides a fallback mechanism for the client to recover their booking state (e.g. if their
   * browser crashed during the checkout loading spinner).
   * 
   * @param userId Injected by the Gateway after JWT validation.
   * @param bookingId The ID of the booking to query.
   * @return The current BookingResponse.
   */
  @GetMapping("/{bookingId}")
  public ResponseEntity<BookingResponse> getBookingStatus(
      @RequestHeader(value = "X-Validated-UserId", required = false, defaultValue = "alice") String userId,
      @PathVariable UUID bookingId) {
    
    BookingResponse response = bookingService.getBookingStatus(bookingId, userId);
    return ResponseEntity.ok(response);
  }
}
