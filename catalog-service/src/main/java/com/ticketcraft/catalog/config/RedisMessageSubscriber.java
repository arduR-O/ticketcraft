package com.ticketcraft.catalog.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketcraft.catalog.controller.SeatStreamController;
import com.ticketcraft.catalog.dto.SeatStreamPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisMessageSubscriber implements MessageListener {

  private final ObjectMapper objectMapper;
  private final SeatStreamController seatStreamController;

  @Override
  public void onMessage(Message message, byte[] pattern) {
    try {
      String json = new String(message.getBody());
      log.debug("Received message from Redis topic: {}", json);
      SeatStreamPayload payload = objectMapper.readValue(json, SeatStreamPayload.class);
      seatStreamController.broadcast(payload.eventId(), payload.updates());
    } catch (Exception e) {
      log.error("Failed to deserialize Redis message", e);
    }
  }
}
