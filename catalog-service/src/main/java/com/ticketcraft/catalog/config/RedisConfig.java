package com.ticketcraft.catalog.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
@org.springframework.context.annotation.Profile("!test")
public class RedisConfig {

  @Bean
  public ChannelTopic seatUpdatesTopic() {
    return new ChannelTopic("seat-updates");
  }

  @Bean
  public RedisMessageListenerContainer redisContainer(
      RedisConnectionFactory connectionFactory,
      RedisMessageSubscriber subscriber,
      ChannelTopic seatUpdatesTopic) {
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);
    container.addMessageListener(subscriber, seatUpdatesTopic);
    return container;
  }
}
