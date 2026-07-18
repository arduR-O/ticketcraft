package com.ticketcraft.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import javax.crypto.SecretKey;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class QueuePassValidationGatewayFilterFactory
    extends AbstractGatewayFilterFactory<QueuePassValidationGatewayFilterFactory.Config> {

  public static final String USER_ID_HEADER = "X-User-Id";
  public static final String QUEUE_PASS_HEADER = "X-Queue-Pass";
  public static final String QUEUE_EVENT_ID_HEADER = "X-Queue-Event-Id";

  private final SecretKey key;
  private final JwtParser jwtParser;
  private final ReactiveStringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  @Value("${queue.max-active-sessions:10000}")
  private int maxActiveSessions;

  public QueuePassValidationGatewayFilterFactory(
      @Value("${queue.jwt.secret}") String jwtSecret,
      ReactiveStringRedisTemplate redisTemplate,
      ObjectMapper objectMapper) {
    super(Config.class);
    this.key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    this.jwtParser = Jwts.parserBuilder().setSigningKey(key).build();
    this.redisTemplate = redisTemplate;
    this.objectMapper = objectMapper;
  }

  @Override
  public GatewayFilter apply(Config config) {
    return (exchange, chain) -> {
      ServerHttpRequest request = exchange.getRequest();
      String path = request.getPath().value();

      // Seat reads can bypass the pass when no queue is active. Writes must always validate.
      if (path.contains("/seatmap") || path.contains("/seat-stream")) {
        String eventId = extractEventIdFromPath(path);
        if (eventId != null) {
          String activeSessionsKey = "{" + eventId + "}:active_sessions";
          String waitlistKey = "{" + eventId + "}:waitlist";

          return redisTemplate
              .opsForZSet()
              .size(activeSessionsKey)
              .defaultIfEmpty(0L)
              .zipWith(redisTemplate.opsForZSet().size(waitlistKey).defaultIfEmpty(0L))
              .flatMap(
                  tuple -> {
                    long activeCount = tuple.getT1();
                    long waitlistCount = tuple.getT2();

                    // If active sessions are below max, and waitlist is empty, validation is
                    // bypassed.
                    if (activeCount < maxActiveSessions && waitlistCount == 0) {
                      log.debug(
                          "Bypassing queue pass check for seatmap. activeCount={},"
                              + " waitlistCount={}",
                          activeCount,
                          waitlistCount);
                      return chain.filter(exchange);
                    }

                    // Otherwise, enforce pass validation
                    return validatePass(exchange, chain);
                  });
        }
      }

      return validatePass(exchange, chain);
    };
  }

  private Mono<Void> validatePass(
      ServerWebExchange exchange,
      org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
    ServerHttpRequest request = exchange.getRequest();
    String queuePass = request.getHeaders().getFirst(QUEUE_PASS_HEADER);
    if (queuePass == null || queuePass.isEmpty()) {
      queuePass = request.getQueryParams().getFirst("queue_pass");
    }

    if (queuePass == null || queuePass.isEmpty()) {
      log.warn("Access denied: Missing X-Queue-Pass header");
      return onError(exchange, "Missing queue pass token", HttpStatus.FORBIDDEN);
    }

    try {
      Claims claims = jwtParser.parseClaimsJws(queuePass).getBody();

      if (claims.getExpiration().before(new Date())) {
        log.warn("Access denied: Queue pass token has expired");
        return onError(exchange, "Expired queue pass token", HttpStatus.FORBIDDEN);
      }

      String path = request.getPath().value();
      String eventIdFromPath = extractEventIdFromPath(path);

      Object eventIdFromClaimObj = claims.get("eventId");
      if (eventIdFromClaimObj == null) {
        log.warn("Access denied: Queue pass token is missing eventId claim");
        return onError(exchange, "Invalid queue pass claims", HttpStatus.FORBIDDEN);
      }

      String eventIdFromClaim = String.valueOf(eventIdFromClaimObj);
      if (eventIdFromPath != null && !eventIdFromPath.equals(eventIdFromClaim)) {
        log.warn(
            "Access denied: Event ID mismatch. path eventId={}, claim eventId={}",
            eventIdFromPath,
            eventIdFromClaim);
        return onError(exchange, "Queue pass event ID mismatch", HttpStatus.FORBIDDEN);
      }

      String userId = claims.getSubject();
      if (userId == null || userId.isEmpty()) {
        log.warn("Access denied: Queue pass token is missing subject");
        return onError(exchange, "Invalid queue pass subject", HttpStatus.FORBIDDEN);
      }

      String authenticatedUserId = request.getHeaders().getFirst(USER_ID_HEADER);
      if (authenticatedUserId != null && !authenticatedUserId.equals(userId)) {
        log.warn(
            "Access denied: Queue pass subject does not match authenticated user. authUser={},"
                + " passUser={}",
            authenticatedUserId,
            userId);
        return onError(exchange, "Queue pass subject mismatch", HttpStatus.FORBIDDEN);
      }

      ServerHttpRequest mutatedRequest =
          request
              .mutate()
              .header(USER_ID_HEADER, userId)
              .header(QUEUE_EVENT_ID_HEADER, eventIdFromClaim)
              .build();

      return chain.filter(exchange.mutate().request(mutatedRequest).build());

    } catch (Exception e) {
      log.warn("Access denied: Invalid queue pass token: {}", e.getMessage());
      return onError(exchange, "Invalid queue pass token: " + e.getMessage(), HttpStatus.FORBIDDEN);
    }
  }

  private String extractEventIdFromPath(String path) {
    try {
      // Handles paths like /api/events/{id}/seatmap and /api/bookings/
      // If path is /api/events/{id}/seatmap, parts: ["", "api", "events", "{id}", "seatmap"]
      String[] parts = path.split("/");
      for (int i = 0; i < parts.length - 1; i++) {
        if ("events".equals(parts[i])) {
          return parts[i + 1];
        }
      }
    } catch (Exception e) {
      log.error("Failed to parse eventId from path: {}", path, e);
    }
    return null;
  }

  private Mono<Void> onError(ServerWebExchange exchange, String err, HttpStatus status) {
    ServerHttpResponse response = exchange.getResponse();
    response.setStatusCode(status);
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
    Map<String, String> errorDetails = Map.of("error", err);
    try {
      byte[] bodyBytes = objectMapper.writeValueAsBytes(errorDetails);
      DataBuffer buffer = response.bufferFactory().wrap(bodyBytes);
      return response.writeWith(Mono.just(buffer));
    } catch (JsonProcessingException e) {
      log.error("Error serializing error details object", e);
      byte[] fallbackBytes = ("{\"error\":\"" + err + "\"}").getBytes(StandardCharsets.UTF_8);
      DataBuffer buffer = response.bufferFactory().wrap(fallbackBytes);
      return response.writeWith(Mono.just(buffer));
    }
  }

  @Data
  public static class Config {
    // Configuration properties can be declared here if filter requires parameters
  }
}
