package com.ticketcraft.catalog.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketcraft.catalog.dto.SeatStatusEvent;
import com.ticketcraft.catalog.dto.SeatStatusUpdate;
import com.ticketcraft.catalog.dto.SeatStreamPayload;
import com.ticketcraft.catalog.repository.SeatRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class SeatStatusConsumer {

  private final SeatRepository seatRepository;
  private final StringRedisTemplate redisTemplate;
  private final ChannelTopic seatUpdatesTopic;
  private final ObjectMapper objectMapper;

  @KafkaListener(topics = "seat-status-changed", groupId = "catalog-seat-consumer")
  @Transactional
  public void consume(SeatStatusEvent event) {
    log.info(
        "Received seat status change event: eventId={}, status={}, seats={}",
        event.eventId(),
        event.newStatus(),
        event.seatIds());

    try {
      int updatedCount =
          seatRepository.updateSeatStatus(event.seatIds(), event.eventId(), event.newStatus());
      log.info("Updated {} seats in DB for event ID {}", updatedCount, event.eventId());

      List<SeatStatusUpdate> updates =
          event.seatIds().stream()
              .map(seatId -> new SeatStatusUpdate(seatId, event.newStatus()))
              .toList();

      SeatStreamPayload payload = new SeatStreamPayload(event.eventId(), updates, Instant.now());
      String json = objectMapper.writeValueAsString(payload);

      redisTemplate.convertAndSend(seatUpdatesTopic.getTopic(), json);
      log.debug("Published seat status updates to Redis topic: {}", seatUpdatesTopic.getTopic());
    } catch (Exception e) {
      log.error("Failed to process seat status update event", e);
      throw new RuntimeException("Failed to process seat status update event", e);
    }
  }
}
