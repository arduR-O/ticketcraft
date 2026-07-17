package com.ticketcraft.booking.exception;

import com.ticketcraft.booking.exception.InvalidBookingRequestException;
import com.ticketcraft.booking.exception.SeatUnavailableException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(SeatUnavailableException.class)
  public ResponseEntity<Map<String, Object>> handleSeatUnavailableException(SeatUnavailableException ex) {
    log.warn("SeatUnavailableException: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
        "error", "Seats unavailable",
        "message", ex.getMessage()
    ));
  }

  @ExceptionHandler(InvalidBookingRequestException.class)
  public ResponseEntity<Map<String, Object>> handleInvalidBookingRequestException(InvalidBookingRequestException ex) {
    log.warn("InvalidBookingRequestException: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
        "error", "Bad Request",
        "message", ex.getMessage()
    ));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> handleGeneralException(Exception ex) {
    log.error("Internal Server Error", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
        "error", "Internal Server Error",
        "message", "An unexpected error occurred."
    ));
  }
}
