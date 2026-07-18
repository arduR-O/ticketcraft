package com.ticketcraft.gateway.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  private final SecretKey key;
  private final long ACCESS_TOKEN_TTL = 15 * 60 * 1000; // 15 minutes
  private final long REFRESH_TOKEN_TTL = 7L * 24 * 60 * 60 * 1000; // 7 days

  public JwtService(@Value("${auth.jwt.secret}") String secret) {
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  }

  public String generateAccessToken(String userId, String email, String role) {
    return Jwts.builder()
        .setSubject(userId)
        .claim("email", email)
        .claim("role", role)
        .claim("type", "access")
        .setId(UUID.randomUUID().toString())
        .setIssuedAt(new Date())
        .setExpiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_TTL))
        .signWith(key, SignatureAlgorithm.HS256)
        .compact();
  }


  public Claims validateToken(String token) {
    try {
      return Jwts.parserBuilder()
          .setSigningKey(key)
          .build()
          .parseClaimsJws(token)
          .getBody();
    } catch (JwtException e) {
      return null;
    }
  }
}
