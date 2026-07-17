package com.ticketcraft.queue.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(QueueController.class)
@ActiveProfiles("test")
class QueueControllerTests {

  @Autowired private WebTestClient webTestClient;

  @MockBean private com.ticketcraft.queue.service.QueueService queueService;

  @Test
  void healthCheck_shouldReturnHealthyStatus() {
    webTestClient
        .get()
        .uri("/api/queue/health")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .isEqualTo("Queue Service is healthy");
  }
}
