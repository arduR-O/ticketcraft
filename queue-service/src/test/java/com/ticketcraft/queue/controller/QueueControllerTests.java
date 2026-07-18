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

  @Test
  void streamQueueUpdates_shouldReturnBadRequest_whenEventIdIsBlank() {
    webTestClient
        .get()
        .uri(
            uriBuilder ->
                uriBuilder
                    .path("/api/queue/stream")
                    .queryParam("eventId", " ")
                    .build())
        .header("X-User-Id", "user123")
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo(400)
        .jsonPath("$.error")
        .isEqualTo("Bad Request")
        .jsonPath("$.message")
        .isEqualTo("eventId must not be blank")
        .jsonPath("$.path")
        .isEqualTo("/api/queue/stream");
  }

  @Test
  void heartbeat_shouldReturnBadRequest_whenUserIdIsBlank() {
    webTestClient
        .post()
        .uri(
            uriBuilder ->
                uriBuilder.path("/api/queue/1001/heartbeat").build())
        .header("X-User-Id", "")
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo(400)
        .jsonPath("$.error")
        .isEqualTo("Bad Request")
        .jsonPath("$.message")
        .isEqualTo("userId must not be blank")
        .jsonPath("$.path")
        .isEqualTo("/api/queue/1001/heartbeat");
  }
}
