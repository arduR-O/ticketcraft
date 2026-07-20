package com.ticketcraft.gateway.security;

import com.ticketcraft.gateway.service.JwtService;
import io.jsonwebtoken.Claims;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(-100) // Ensure this runs before routing
public class JwtAuthenticationWebFilter implements WebFilter {

  private final JwtService jwtService;
  private final ReactiveStringRedisTemplate redisTemplate;

  public JwtAuthenticationWebFilter(
      JwtService jwtService, ReactiveStringRedisTemplate redisTemplate) {
    this.jwtService = jwtService;
    this.redisTemplate = redisTemplate;
  }

  /**
   * Intercepts incoming requests, validates the JWT access token, checks the Redis blacklist,
   * and attaches the user ID as a downstream HTTP header. Bypasses public routes.
   */
  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String path = exchange.getRequest().getPath().value();

    // Skip public routes and auth endpoints
    if (exchange.getRequest().getMethod().name().equals("OPTIONS")
        || path.startsWith("/api/v1/auth")
        || path.equals("/api/v1/events/search")
        || path.equals("/api/v1/events/nearby")
        || path.matches("/api/v1/events/\\d+")
        || path.matches("/api/v1/events/\\d+/seat-stream")
        || path.startsWith("/login")
        || path.startsWith("/oauth2")) {
      return chain.filter(exchange);
    }

    String token = resolveAccessToken(exchange);
    if (token == null) {
      return onError(exchange, HttpStatus.UNAUTHORIZED);
    }

    Claims claims = jwtService.validateToken(token);

    if (claims == null || !"access".equals(claims.get("type"))) {
      return onError(exchange, HttpStatus.UNAUTHORIZED);
    }

    String jti = claims.getId();
    String userId = claims.getSubject();
    String role = claims.get("role", String.class);

    // Check blacklist in Redis
    return redisTemplate
        .hasKey("revoked:token:" + jti)
        .flatMap(
            isRevoked -> {
              if (Boolean.TRUE.equals(isRevoked)) {
                return onError(exchange, HttpStatus.UNAUTHORIZED);
              }

              // Mutate request to add X-User-Id and X-User-Role headers for downstream services
              ServerHttpRequest mutatedRequest =
                  exchange.getRequest().mutate()
                      .header("X-User-Id", userId)
                      .header("X-User-Role", role != null ? role : "USER")
                      .build();

              return chain.filter(exchange.mutate().request(mutatedRequest).build());
            });
  }

  /**
   * Helper method to return an immediate HTTP error response and terminate the request chain.
   */
  private Mono<Void> onError(ServerWebExchange exchange, HttpStatus status) {
    exchange.getResponse().setStatusCode(status);
    return exchange.getResponse().setComplete();
  }

  /**
   * Extracts the access token from the Authorization header or query parameters for SSE streams.
   */
  private String resolveAccessToken(ServerWebExchange exchange) {
    String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      return authHeader.substring(7);
    }

    String path = exchange.getRequest().getPath().value();
    if (path.equals("/api/v1/queue/stream")) {
      return exchange.getRequest().getQueryParams().getFirst("access_token");
    }

    return null;
  }
}
