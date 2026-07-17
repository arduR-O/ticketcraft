package com.ticketcraft.booking.kafka;

import com.ticketcraft.booking.event.BookingCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingCreatedProducer {

  private final KafkaTemplate<String, Object> kafkaTemplate;
  private static final String TOPIC = "booking-created";

  public void sendBookingCreated(BookingCreatedEvent event) {
    // Partition key is bookingId
    String key = event.getBookingId();
    log.info("Producing BookingCreatedEvent to topic {}: {}", TOPIC, event);
    kafkaTemplate.send(TOPIC, key, event);
  }
}
