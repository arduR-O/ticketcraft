package com.ticketcraft.gateway.security;

import com.ticketcraft.gateway.entity.User;
import com.ticketcraft.gateway.repository.UserRepository;
import com.ticketcraft.gateway.service.JwtService;
import com.ticketcraft.gateway.service.RefreshTokenService;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpCookie;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements ServerAuthenticationSuccessHandler {

  private final UserRepository userRepository;
  private final JwtService jwtService;
  private final ReactiveStringRedisTemplate redisTemplate;
  private final RefreshTokenService refreshTokenService;

  @Override
  public Mono<Void> onAuthenticationSuccess(
      WebFilterExchange webFilterExchange, Authentication authentication) {
    OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
    OAuth2User oauthUser = oauthToken.getPrincipal();
    String email = oauthUser.getAttribute("email");

    return userRepository
        .findByEmail(email)
        .switchIfEmpty(
            Mono.defer(
                () -> {
                  User newUser =
                      User.builder()
                          .id(UUID.randomUUID())
                          .email(email)
                          .provider("GOOGLE")
                          .role("USER")
                          .createdAt(LocalDateTime.now())
                          .build();
                  return userRepository.save(newUser);
                }))
        .flatMap(
            user -> {
              String exchangeCode = UUID.randomUUID().toString();
              String accessToken =
                  jwtService.generateAccessToken(user.getId().toString(), user.getEmail(), user.getRole());
              
              return refreshTokenService.createRefreshToken(user.getId().toString())
                  .flatMap(refreshToken -> {
                      // Store the mapping in Redis for 60 seconds
                      String payload = accessToken + ":" + refreshToken;
                      return redisTemplate
                          .opsForValue()
                          .set(exchangeCode, payload, Duration.ofSeconds(60))
                          .then(
                              Mono.defer(
                                  () -> {
                                    webFilterExchange
                                        .getExchange()
                                        .getResponse()
                                        .setStatusCode(org.springframework.http.HttpStatus.FOUND);
                                    webFilterExchange
                                        .getExchange()
                                        .getResponse()
                                        .getHeaders()
                                        .setLocation(
                                            URI.create(
                                                "http://localhost:3000/oauth2/redirect?code="
                                                    + exchangeCode));
                                    return webFilterExchange.getExchange().getResponse().setComplete();
                                  }));
                  });
            });
  }
}
