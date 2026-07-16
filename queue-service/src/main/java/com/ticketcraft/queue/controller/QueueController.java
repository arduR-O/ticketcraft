package com.ticketcraft.queue.controller;

import com.ticketcraft.queue.dto.QueueStatus;
import com.ticketcraft.queue.service.QueueService;
import java.time.Duration;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/queue")
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
  public Flux<ServerSentEvent<QueueStatus>> streamQueueUpdates(@RequestParam String userId) {
    return queueService
        .enqueueUser(userId)
        .thenMany(Flux.interval(Duration.ofSeconds(5)).flatMap(tick -> checkStatus(userId)));
  }

  private Mono<ServerSentEvent<QueueStatus>> checkStatus(String userId) {
    return queueService
        .getPromotionPass(userId)
        .map(
            passToken ->
                ServerSentEvent.<QueueStatus>builder()
                    .data(QueueStatus.builder().status("PROMOTED").passToken(passToken).build())
                    .build())
        .switchIfEmpty(
            queueService
                .getQueuePosition(userId)
                .map(
                    position ->
                        ServerSentEvent.<QueueStatus>builder()
                            .data(
                                QueueStatus.builder()
                                    .status("WAITING")
                                    .position(position)
                                    .build())
                            .build())
                .defaultIfEmpty(
                    ServerSentEvent.<QueueStatus>builder()
                        .data(QueueStatus.builder().status("DISCONNECTED").build())
                        .build()));
  }
}
