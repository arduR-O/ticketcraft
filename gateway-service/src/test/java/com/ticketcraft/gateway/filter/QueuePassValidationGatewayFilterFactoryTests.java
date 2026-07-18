package com.ticketcraft.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveZSetOperations;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@SpringBootTest
@ActiveProfiles("test")
public class QueuePassValidationGatewayFilterFactoryTests {

  @Autowired private QueuePassValidationGatewayFilterFactory filterFactory;

  @MockBean private ReactiveStringRedisTemplate redisTemplate;

  private ReactiveZSetOperations<String, String> zSetOperations;

  private final String secret = "ticketcraft_waiting_room_secret_key_32_bytes_long_minimum!";
  private SecretKey key;

  @BeforeEach
  @SuppressWarnings("unchecked")
  public void setUp() {
    zSetOperations = Mockito.mock(ReactiveZSetOperations.class);
    when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  }

  private String createToken(String userId, String eventId, long ttlMs) {
    return Jwts.builder()
        .setSubject(userId)
        .claim("eventId", eventId)
        .setExpiration(new Date(System.currentTimeMillis() + ttlMs))
        .signWith(key, SignatureAlgorithm.HS256)
        .compact();
  }

  @Test
  public void shouldBlockWhenMissingHeaderAndQueueIsActive() {
    when(zSetOperations.size(anyString())).thenReturn(Mono.just(10000L));

    MockServerHttpRequest request = MockServerHttpRequest.get("/api/events/1001/seatmap").build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    GatewayFilter filter =
        filterFactory.apply(new QueuePassValidationGatewayFilterFactory.Config());
    GatewayFilterChain filterChain = Mockito.mock(GatewayFilterChain.class);
    when(filterChain.filter(exchange)).thenReturn(Mono.empty());

    Mono<Void> result = filter.filter(exchange, filterChain);

    StepVerifier.create(result).verifyComplete();

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  public void shouldBypassValidationWhenQueueIsInactive() {
    when(zSetOperations.size("{1001}:active_sessions")).thenReturn(Mono.just(100L));
    when(zSetOperations.size("{1001}:waitlist")).thenReturn(Mono.just(0L));

    MockServerHttpRequest request = MockServerHttpRequest.get("/api/events/1001/seatmap").build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    GatewayFilter filter =
        filterFactory.apply(new QueuePassValidationGatewayFilterFactory.Config());
    GatewayFilterChain filterChain = Mockito.mock(GatewayFilterChain.class);
    when(filterChain.filter(exchange)).thenReturn(Mono.empty());

    Mono<Void> result = filter.filter(exchange, filterChain);

    StepVerifier.create(result).verifyComplete();

    assertThat(exchange.getResponse().getStatusCode()).isNull();
  }

  @Test
  public void shouldInjectHeaderWhenTokenIsValid() {
    when(zSetOperations.size(anyString())).thenReturn(Mono.just(10000L));

    String token = createToken("user123", "1001", 60000);
    MockServerHttpRequest request =
        MockServerHttpRequest.get("/api/events/1001/seatmap").header("X-Queue-Pass", token).build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    GatewayFilter filter =
        filterFactory.apply(new QueuePassValidationGatewayFilterFactory.Config());
    GatewayFilterChain filterChain =
        (exch) -> {
          String injectedHeader = exch.getRequest().getHeaders().getFirst("X-User-Id");
          String injectedEventId = exch.getRequest().getHeaders().getFirst("X-Queue-Event-Id");
          assertThat(injectedHeader).isEqualTo("user123");
          assertThat(injectedEventId).isEqualTo("1001");
          return Mono.empty();
        };

    Mono<Void> result = filter.filter(exchange, filterChain);

    StepVerifier.create(result).verifyComplete();

    assertThat(exchange.getResponse().getStatusCode()).isNull();
  }

  @Test
  public void shouldBlockWhenTokenEventIdMismatch() {
    when(zSetOperations.size(anyString())).thenReturn(Mono.just(10000L));

    String token = createToken("user123", "9999", 60000);
    MockServerHttpRequest request =
        MockServerHttpRequest.get("/api/events/1001/seatmap").header("X-Queue-Pass", token).build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    GatewayFilter filter =
        filterFactory.apply(new QueuePassValidationGatewayFilterFactory.Config());
    GatewayFilterChain filterChain = Mockito.mock(GatewayFilterChain.class);
    when(filterChain.filter(exchange)).thenReturn(Mono.empty());

    Mono<Void> result = filter.filter(exchange, filterChain);

    StepVerifier.create(result).verifyComplete();

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  public void shouldBlockWhenQueuePassSubjectDoesNotMatchAuthenticatedUser() {
    when(zSetOperations.size(anyString())).thenReturn(Mono.just(10000L));

    String token = createToken("user123", "1001", 60000);
    MockServerHttpRequest request =
        MockServerHttpRequest.get("/api/events/1001/seatmap")
            .header("X-User-Id", "other-user")
            .header("X-Queue-Pass", token)
            .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    GatewayFilter filter =
        filterFactory.apply(new QueuePassValidationGatewayFilterFactory.Config());
    GatewayFilterChain filterChain = Mockito.mock(GatewayFilterChain.class);
    when(filterChain.filter(exchange)).thenReturn(Mono.empty());

    Mono<Void> result = filter.filter(exchange, filterChain);

    StepVerifier.create(result).verifyComplete();

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }
}
