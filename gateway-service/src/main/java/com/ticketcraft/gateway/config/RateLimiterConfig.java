package com.ticketcraft.gateway.config;

import java.util.Objects;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimiterConfig {

  @Bean
  public KeyResolver ipKeyResolver() {
    return exchange ->
        Mono.just(
            Objects.requireNonNull(exchange.getRequest().getRemoteAddress())
                .getAddress()
                .getHostAddress());
  }
}
