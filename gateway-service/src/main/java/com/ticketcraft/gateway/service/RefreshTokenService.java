package com.ticketcraft.gateway.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class RefreshTokenService {

  private final ReactiveStringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private static final Duration TTL = Duration.ofDays(7);

  public RefreshTokenService(ReactiveStringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
    this.redisTemplate = redisTemplate;
    this.objectMapper = objectMapper;
  }

  /**
   * Generates a new refresh token and saves it to Redis as the root of a new token family.
   */
  public Mono<String> createRefreshToken(String userId) {
    String tokenId = UUID.randomUUID().toString();
    String familyId = tokenId; // Root token is its own family
    return saveTokenRecord(tokenId, familyId, userId, false)
        .then(redisTemplate.opsForSet().add("family:" + familyId, tokenId))
        .then(redisTemplate.expire("family:" + familyId, TTL))
        .thenReturn(tokenId);
  }

  /**
   * Rotates an existing refresh token by marking it as used and issuing a new one in the same family. 
   * If reuse of a previously rotated token is detected, it revokes the entire family.
   */
  public Mono<RotationResult> rotateRefreshToken(String oldTokenId) {
    String tokenKey = "refresh:" + oldTokenId;
    return redisTemplate.opsForValue().get(tokenKey)
        .flatMap(json -> {
          try {
            @SuppressWarnings("unchecked")
            Map<String, Object> record = objectMapper.readValue(json, Map.class);
            String familyId = (String) record.get("familyId");
            String userId = (String) record.get("userId");
            boolean used = (Boolean) record.get("used");

            if (used) {
              // REUSE DETECTED! Revoke family and return empty (unauthorized)
              return revokeFamilyByFamilyId(familyId)
                  .then(Mono.empty());
            } else {
              // Valid. Mark old as used.
              record.put("used", true);
              return redisTemplate.opsForValue()
                  .set(tokenKey, objectMapper.writeValueAsString(record), TTL)
                  .then(Mono.defer(() -> {
                    String newTokenId = UUID.randomUUID().toString();
                    return saveTokenRecord(newTokenId, familyId, userId, false)
                        .then(redisTemplate.opsForSet().add("family:" + familyId, newTokenId))
                        .then(redisTemplate.expire("family:" + familyId, TTL))
                        .thenReturn(new RotationResult(newTokenId, userId));
                  }));
            }
          } catch (Exception e) {
            return Mono.empty();
          }
        });
  }

  /**
   * Looks up a token's family ID and revokes all tokens within that family.
   */
  public Mono<Void> revokeFamilyByTokenId(String tokenId) {
    String tokenKey = "refresh:" + tokenId;
    return redisTemplate.opsForValue().get(tokenKey)
        .flatMap(json -> {
          try {
            @SuppressWarnings("unchecked")
            Map<String, Object> record = objectMapper.readValue(json, Map.class);
            String familyId = (String) record.get("familyId");
            return revokeFamilyByFamilyId(familyId);
          } catch (Exception e) {
            return Mono.empty();
          }
        });
  }

  /**
   * Deletes all tokens belonging to a specific family ID from Redis.
   */
  private Mono<Void> revokeFamilyByFamilyId(String familyId) {
    String familyKey = "family:" + familyId;
    return redisTemplate.opsForSet().members(familyKey)
        .flatMap(tokenId -> redisTemplate.delete("refresh:" + tokenId))
        .then(redisTemplate.delete(familyKey))
        .then();
  }

  /**
   * Helper method to serialize and save a token record JSON payload to Redis.
   */
  private Mono<Boolean> saveTokenRecord(String tokenId, String familyId, String userId, boolean used) {
    try {
      Map<String, Object> record = Map.of(
          "userId", userId,
          "familyId", familyId,
          "used", used,
          "createdAt", Instant.now().toEpochMilli()
      );
      return redisTemplate.opsForValue()
          .set("refresh:" + tokenId, objectMapper.writeValueAsString(record), TTL);
    } catch (JsonProcessingException e) {
      return Mono.error(e);
    }
  }

  public static class RotationResult {
    public final String newTokenId;
    public final String userId;

    public RotationResult(String newTokenId, String userId) {
      this.newTokenId = newTokenId;
      this.userId = userId;
    }
  }
}
