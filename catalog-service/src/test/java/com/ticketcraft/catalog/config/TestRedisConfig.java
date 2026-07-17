package com.ticketcraft.catalog.config;

import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;

@Configuration
@Profile("test")
public class TestRedisConfig {

  @Bean
  @Primary
  public ChannelTopic seatUpdatesTopic() {
    return new ChannelTopic("seat-updates");
  }

  @Bean
  @Primary
  public StringRedisTemplate stringRedisTemplate() {
    return Mockito.mock(StringRedisTemplate.class);
  }
}
