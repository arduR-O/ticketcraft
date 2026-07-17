package com.ticketcraft.booking.kafka;

import com.ticketcraft.booking.event.SeatStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatStatusProducer {

  private final KafkaTemplate<String, Object> kafkaTemplate;
  private static final String TOPIC = "seat-status-changed";

  public void sendSeatStatusChanged(SeatStatusChangedEvent event) {
    // Partition key is eventId for correct ordering of seat status per event
    String key = String.valueOf(event.getEventId());
    log.info("Producing SeatStatusChangedEvent to topic {}: {}", TOPIC, event);
    kafkaTemplate.send(TOPIC, key, event);
  }
}
