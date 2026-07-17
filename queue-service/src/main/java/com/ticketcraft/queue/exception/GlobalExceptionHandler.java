package com.ticketcraft.queue.exception;

import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.support.WebExchangeBindException;

@ControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<Map<String, Object>> handleConstraintViolation(
      ConstraintViolationException ex, ServerHttpRequest request) {
    String message = ex.getConstraintViolations().iterator().next().getMessage();
    return buildResponse(HttpStatus.BAD_REQUEST, "Bad Request", message, request);
  }

  @ExceptionHandler(WebExchangeBindException.class)
  public ResponseEntity<Map<String, Object>> handleWebExchangeBind(
      WebExchangeBindException ex, ServerHttpRequest request) {
    String message =
        ex.getBindingResult().getFieldError() != null
            ? ex.getBindingResult().getFieldError().getDefaultMessage()
            : ex.getMessage();
    return buildResponse(HttpStatus.BAD_REQUEST, "Bad Request", message, request);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, Object>> handleIllegalArgument(
      IllegalArgumentException ex, ServerHttpRequest request) {
    return buildResponse(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), request);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> handleGeneralException(
      Exception ex, ServerHttpRequest request) {
    return buildResponse(
        HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", ex.getMessage(), request);
  }

  private ResponseEntity<Map<String, Object>> buildResponse(
      HttpStatus status, String error, String message, ServerHttpRequest request) {
    Map<String, Object> body = new HashMap<>();
    body.put("timestamp", Instant.now().toString());
    body.put("status", status.value());
    body.put("error", error);
    body.put("message", message);
    body.put("path", request.getURI().getPath());
    return new ResponseEntity<>(body, status);
  }
}
