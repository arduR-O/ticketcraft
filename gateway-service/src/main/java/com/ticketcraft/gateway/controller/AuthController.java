package com.ticketcraft.gateway.controller;

import com.ticketcraft.gateway.entity.User;
import com.ticketcraft.gateway.repository.UserRepository;
import com.ticketcraft.gateway.service.JwtService;
import com.ticketcraft.gateway.service.RefreshTokenService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final ReactiveStringRedisTemplate redisTemplate;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final RefreshTokenService refreshTokenService;

  public AuthController(
      ReactiveStringRedisTemplate redisTemplate,
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      RefreshTokenService refreshTokenService) {
    this.redisTemplate = redisTemplate;
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.refreshTokenService = refreshTokenService;
  }

  @PostMapping("/token")
  public Mono<ResponseEntity<Map<String, String>>> exchangeToken(
      @RequestBody Map<String, String> request) {
    String code = request.get("code");
    if (code == null || code.isEmpty()) {
      return Mono.just(ResponseEntity.badRequest().build());
    }

    return redisTemplate
        .opsForValue()
        .getAndDelete(code)
        .map(
            payload -> {
              String[] tokens = payload.split(":");
              ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", tokens[1])
                  .httpOnly(true)
                  .path("/api/v1/auth")
                  .maxAge(Duration.ofDays(7))
                  .sameSite("Strict")
                  .build();
              return ResponseEntity.ok()
                  .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                  .body(Map.of("accessToken", tokens[0]));
            })
        .switchIfEmpty(Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()));
  }

  @PostMapping("/register")
  public Mono<ResponseEntity<Map<String, String>>> register(
      @RequestBody Map<String, String> request) {
    String email = request.get("email");
    String password = request.get("password");

    if (email == null || password == null) {
      return Mono.just(ResponseEntity.badRequest().build());
    }

    return userRepository
        .findByEmail(email)
        .flatMap(
            existing ->
                Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Email exists"))))
        .switchIfEmpty(
            Mono.defer(
                () -> {
                  User newUser =
                      User.builder()
                          .id(java.util.UUID.randomUUID())
                          .email(email)
                          .passwordHash(passwordEncoder.encode(password))
                          .provider("LOCAL")
                          .role("USER")
                          .createdAt(LocalDateTime.now())
                          .isNew(true)
                          .build();
                  return userRepository
                      .save(newUser)
                      .map(saved -> ResponseEntity.ok(Map.of("message", "Registered successfully")));
                }));
  }

  @PostMapping("/login")
  public Mono<ResponseEntity<Map<String, String>>> login(
      @RequestBody Map<String, String> request) {
    String email = request.get("email");
    String password = request.get("password");

    return userRepository
        .findByEmail(email)
        .filter(user -> "LOCAL".equals(user.getProvider()) && passwordEncoder.matches(password, user.getPasswordHash()))
        .flatMap(
            user -> {
              String accessToken =
                  jwtService.generateAccessToken(user.getId().toString(), user.getEmail(), user.getRole());
              return refreshTokenService.createRefreshToken(user.getId().toString())
                  .map(refreshToken -> {
                    ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", refreshToken)
                        .httpOnly(true)
                        .path("/api/v1/auth")
                        .maxAge(Duration.ofDays(7))
                        .sameSite("Strict")
                        .build();
                    return ResponseEntity.ok()
                        .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                        .body(Map.of("accessToken", accessToken));
                  });
            })
        .switchIfEmpty(Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()));
  }

  @PostMapping("/refresh")
  public Mono<ResponseEntity<Map<String, String>>> refresh(
      @CookieValue(name = "refreshToken", required = false) String refreshToken) {
    if (refreshToken == null || refreshToken.isEmpty()) {
      return Mono.just(ResponseEntity.badRequest().build());
    }

    return refreshTokenService.rotateRefreshToken(refreshToken)
        .flatMap(result -> userRepository
            .findById(java.util.UUID.fromString(result.userId))
            .map(user -> {
              String newAccessToken = jwtService.generateAccessToken(
                  user.getId().toString(), user.getEmail(), user.getRole());
              ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", result.newTokenId)
                  .httpOnly(true)
                  .path("/api/v1/auth")
                  .maxAge(Duration.ofDays(7))
                  .sameSite("Strict")
                  .build();
              return ResponseEntity.ok()
                  .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                  .body(Map.of("accessToken", newAccessToken));
            })
        )
        .switchIfEmpty(Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()));
  }

  @PostMapping("/logout")
  public Mono<ResponseEntity<Void>> logout(
      @CookieValue(name = "refreshToken", required = false) String refreshToken) {
    ResponseCookie clearCookie = ResponseCookie.from("refreshToken", "")
        .httpOnly(true)
        .path("/api/v1/auth")
        .maxAge(0)
        .sameSite("Strict")
        .build();

    if (refreshToken == null || refreshToken.isEmpty()) {
      return Mono.just(ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, clearCookie.toString()).build());
    }

    return refreshTokenService.revokeFamilyByTokenId(refreshToken)
        .then(Mono.just(ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, clearCookie.toString()).<Void>build()));
  }
}
