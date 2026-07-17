package com.ticketcraft.booking.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

  @Value("${redisson.single-server-config.address}")
  private String redisAddress;

  @Value("${redisson.single-server-config.password:}")
  private String redisPassword;

  @Bean
  public RedissonClient redissonClient() {
    Config config = new Config();
    var serverConfig = config.useSingleServer().setAddress(redisAddress);
    
    if (redisPassword != null && !redisPassword.isEmpty()) {
      serverConfig.setPassword(redisPassword);
    }
    
    return Redisson.create(config);
  }
}
