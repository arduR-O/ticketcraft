package com.ticketcraft.gateway.controller;

import java.time.Duration;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

  private final ReactiveStringRedisTemplate redisTemplate;

  /**
   * Manually revokes a specific access token by its JTI (JWT ID).
   * This is a utility for developers/admins to forcibly invalidate a stolen or rogue token.
   */
  @PostMapping("/revoke-access")
  public Mono<ResponseEntity<Void>> revokeAccessToken(
      @RequestHeader(value = "X-User-Role", required = false) String role,
      @RequestBody Map<String, String> request) {
    
    // Ensure the caller is an authenticated ADMIN
    if (!"ADMIN".equals(role)) {
      return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
    }

    String jti = request.get("jti");
    if (jti == null || jti.isEmpty()) {
      return Mono.just(ResponseEntity.badRequest().build());
    }

    // Access tokens are short-lived. A 1-hour blacklist TTL is more than enough
    return redisTemplate.opsForValue()
        .set("revoked:token:" + jti, "true", Duration.ofHours(1))
        .then(Mono.just(ResponseEntity.ok().<Void>build()));
  }
}
