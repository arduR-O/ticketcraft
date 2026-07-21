package com.ticketcraft.queue.controller;

import com.ticketcraft.queue.dto.QueueStatus;
import com.ticketcraft.queue.service.QueueService;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive REST Controller exposing endpoints for streaming queue status, receiving heartbeats, and health checks.
 * 
 * This is built with Spring WebFlux (Reactor) because it must handle thousands of long-lived
 * concurrent connections (SSE) without blocking threads. A traditional thread-per-request model (like WebMVC)
 * would quickly exhaust server thread pools under heavy waiting room load.
 */
@RestController
@RequestMapping("/api/queue")
@Validated
public class QueueController {

  private final QueueService queueService;

  public QueueController(QueueService queueService) {
    this.queueService = queueService;
  }

  @GetMapping("/health")
  public Mono<String> healthCheck() {
    return Mono.just("Queue Service is healthy");
  }

  /**
   * Endpoint for clients to join the queue and receive live updates.
   * Returns a Server-Sent Events (SSE) stream by enqueuing the user and then yielding a stream
   * of QueueStatus objects.
   * 
   * SSE is utilized here because it is highly efficient for unidirectional status updates compared to WebSockets.
   * The polling interval inside the Flux ensures the client sees their position move up in near real-time without
   * overwhelming Redis with constant lookups.
   * 
   * @param eventId The event ID.
   * @param userId The user ID injected by the Gateway after access-token validation.
   * @return A Flux of ServerSentEvents containing QueueStatus.
   */
  @GetMapping(value = "/stream", produces = "text/event-stream")
  public Flux<ServerSentEvent<QueueStatus>> streamQueueUpdates(
      @RequestParam @NotBlank(message = "eventId must not be blank") String eventId,
      @RequestHeader("X-User-Id") @NotBlank(message = "userId must not be blank") String userId) {
    return queueService
        .enqueueUser(eventId, userId)
        .thenMany(
            Flux.interval(Duration.ZERO, Duration.ofSeconds(2)).flatMap(tick -> checkStatus(eventId, userId)));
  }

  /**
   * Endpoint for active clients to signal they are still alive.
   * Processes a heartbeat for the user in the given event's active session.
   * 
   * This mechanism is crucial to prevent users who joined the queue but closed their browser tab
   * from holding up the line indefinitely. The backend worker evicts users without recent heartbeats.
   * 
   * @param eventId The event ID.
   * @param userId The user ID injected by the Gateway after access-token validation.
   * @return 200 OK if successful, 404 if user is not in the active session.
   */
  @PostMapping("/{eventId}/heartbeat")
  public Mono<ResponseEntity<Map<String, String>>> heartbeat(
      @PathVariable @NotBlank(message = "eventId must not be blank") String eventId,
      @RequestHeader("X-User-Id") @NotBlank(message = "userId must not be blank") String userId) {
    return queueService
        .processHeartbeat(eventId, userId)
        .map(
            success -> {
              if (success) {
                return ResponseEntity.ok(Map.of("status", "OK"));
              } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "User not found in active sessions"));
              }
            });
  }

  /**
   * Endpoint to attempt bypassing the queue.
   * If the queue is inactive/empty, it returns a pass token immediately.
   * 
   * This endpoint is provided to simplify frontend logic, allowing the client to always 
   * ask for a pass directly. If denied with a 409 Conflict, the frontend knows it must 
   * fall back to joining the SSE queue stream.
   * 
   * @return 200 OK with passToken, or 409 Conflict if the queue is active.
   */
  @GetMapping("/{eventId}/pass")
  public Mono<ResponseEntity<Map<String, String>>> getFastTrackPass(
      @PathVariable @NotBlank(message = "eventId must not be blank") String eventId,
      @RequestHeader("X-User-Id") @NotBlank(message = "userId must not be blank") String userId) {
    return queueService.attemptFastTrack(eventId, userId)
        .map(passToken -> ResponseEntity.ok(Map.of("passToken", passToken)))
        .defaultIfEmpty(ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Queue is currently active")));
  }

  private Mono<ServerSentEvent<QueueStatus>> checkStatus(String eventId, String userId) {
    return queueService
        .getPromotionPass(eventId, userId)
        .map(
            passToken ->
                ServerSentEvent.<QueueStatus>builder()
                    .data(QueueStatus.builder().status("PROMOTED").passToken(passToken).build())
                    .build())
        .switchIfEmpty(
            queueService
                .getQueuePosition(eventId, userId)
                .map(
                    position -> {
                      long humanPosition = position + 1; // 0-based rank to 1-based position
                      long minutes = Math.max(1, humanPosition / 100);
                      String estimatedWait = minutes + " min";
                      return ServerSentEvent.<QueueStatus>builder()
                          .data(
                              QueueStatus.builder()
                                  .status("WAITING")
                                  .position(humanPosition)
                                  .estimatedWait(estimatedWait)
                                  .build())
                          .build();
                    })
                .defaultIfEmpty(
                    ServerSentEvent.<QueueStatus>builder()
                        .data(QueueStatus.builder().status("DISCONNECTED").build())
                        .build()));
  }
}
