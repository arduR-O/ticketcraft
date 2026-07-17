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
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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

  @GetMapping(value = "/stream", produces = "text/event-stream")
  public Flux<ServerSentEvent<QueueStatus>> streamQueueUpdates(
      @RequestParam @NotBlank(message = "eventId must not be blank") String eventId,
      @RequestParam @NotBlank(message = "userId must not be blank") String userId) {
    return queueService
        .enqueueUser(eventId, userId)
        .thenMany(
            Flux.interval(Duration.ofSeconds(5)).flatMap(tick -> checkStatus(eventId, userId)));
  }

  @PostMapping("/{eventId}/heartbeat")
  public Mono<ResponseEntity<Map<String, String>>> heartbeat(
      @PathVariable @NotBlank(message = "eventId must not be blank") String eventId,
      @RequestParam @NotBlank(message = "userId must not be blank") String userId) {
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
