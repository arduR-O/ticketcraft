package com.ticketcraft.booking.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

  @Bean
  public NewTopic seatStatusChangedTopic() {
    return TopicBuilder.name("seat-status-changed")
        .partitions(6)
        .replicas(1)
        .build();
  }
}
