package com.ticketcraft.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

  @Bean
  public SecurityWebFilterChain securityWebFilterChain(
      ServerHttpSecurity http,
      com.ticketcraft.gateway.security.OAuth2LoginSuccessHandler successHandler) {
    return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
        .authorizeExchange(
            exchanges ->
                exchanges
                    .pathMatchers("/login/**", "/oauth2/**", "/api/v1/auth/**")
                    .permitAll()
                    .anyExchange()
                    .permitAll() // the JwtAuthenticationWebFilter will enforce security
            )
        .oauth2Login(oauth2 -> oauth2.authenticationSuccessHandler(successHandler))
        .build();
  }

  @Bean
  public org.springframework.security.crypto.password.PasswordEncoder passwordEncoder() {
    return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
  }
}
