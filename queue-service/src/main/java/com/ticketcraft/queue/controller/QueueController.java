package com.ticketcraft.queue.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/queue")
public class QueueController {

  @GetMapping("/health")
  public Mono<String> healthCheck() {
    return Mono.just("Queue Service is healthy");
  }
}
