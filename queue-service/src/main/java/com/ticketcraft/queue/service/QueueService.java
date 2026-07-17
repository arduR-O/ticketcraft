package com.ticketcraft.queue.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class QueueService {

  private final ReactiveRedisTemplate<String, String> redisTemplate;
  private final RedisScript<List> promoteScript;

  @Value("${queue.jwt.secret}")
  private String jwtSecret;

  @Value("${queue.max-active-sessions}")
  private int maxActiveSessions;

  @Value("${queue.heartbeat-grace-seconds}")
  private int heartbeatGraceSeconds;

  public QueueService(ReactiveRedisTemplate<String, String> redisTemplate) {
    this.redisTemplate = redisTemplate;
    DefaultRedisScript<List> script = new DefaultRedisScript<>();
    script.setLocation(new ClassPathResource("scripts/promote_users.lua"));
    script.setResultType(List.class);
    this.promoteScript = script;
  }

  private String getWaitlistKey(String eventId) {
    return "{" + eventId + "}:waitlist";
  }

  private String getActiveSessionsKey(String eventId) {
    return "{" + eventId + "}:active_sessions";
  }

  private String getHeartbeatsKey(String eventId) {
    return "{" + eventId + "}:heartbeats";
  }

  private Key getSigningKey() {
    return Keys.hmacShaKeyFor(jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  public String generatePassToken(String userId, String eventId) {
    long now = Instant.now().toEpochMilli();
    return Jwts.builder()
        .setSubject(userId)
        .claim("eventId", eventId)
        .setIssuedAt(new Date(now))
        .setExpiration(new Date(now + 300000)) // 5 minute TTL
        .signWith(getSigningKey(), SignatureAlgorithm.HS256)
        .compact();
  }

  public Mono<Long> enqueueUser(String eventId, String userId) {
    String waitlistKey = getWaitlistKey(eventId);
    double score = Instant.now().toEpochMilli();
    return redisTemplate
        .opsForSet()
        .add("active_event_queues", eventId)
        .then(redisTemplate.opsForZSet().add(waitlistKey, userId, score))
        .then(redisTemplate.opsForZSet().rank(waitlistKey, userId));
  }

  public Mono<Long> getQueuePosition(String eventId, String userId) {
    return redisTemplate.opsForZSet().rank(getWaitlistKey(eventId), userId);
  }

  public Mono<String> getPromotionPass(String eventId, String userId) {
    return redisTemplate
        .opsForZSet()
        .score(getActiveSessionsKey(eventId), userId)
        .flatMap(
            score -> {
              if (score != null) {
                return Mono.just(generatePassToken(userId, eventId));
              }
              return Mono.empty();
            });
  }

  public Mono<Boolean> processHeartbeat(String eventId, String userId) {
    String activeSessionsKey = getActiveSessionsKey(eventId);
    String heartbeatsKey = getHeartbeatsKey(eventId);
    return redisTemplate
        .opsForZSet()
        .score(activeSessionsKey, userId)
        .flatMap(
            score -> {
              if (score != null) {
                return redisTemplate
                    .opsForZSet()
                    .add(heartbeatsKey, userId, (double) Instant.now().toEpochMilli())
                    .thenReturn(true);
              }
              return Mono.just(false);
            })
        .defaultIfEmpty(false);
  }

  public Flux<String> getActiveEventQueues() {
    return redisTemplate.opsForSet().members("active_event_queues");
  }

  @SuppressWarnings("unchecked")
  public Mono<Void> promoteUsers(String eventId) {
    List<String> keys =
        List.of(getWaitlistKey(eventId), getActiveSessionsKey(eventId), getHeartbeatsKey(eventId));
    List<String> args =
        List.of(
            String.valueOf(maxActiveSessions),
            String.valueOf(heartbeatGraceSeconds * 1000),
            String.valueOf(Instant.now().toEpochMilli()));

    return redisTemplate
        .execute(promoteScript, keys, args)
        .next()
        .flatMap(
            list -> {
              // Check if both queues are empty to clean up the active event queues set
              return redisTemplate
                  .opsForZSet()
                  .size(getWaitlistKey(eventId))
                  .zipWith(redisTemplate.opsForZSet().size(getActiveSessionsKey(eventId)))
                  .flatMap(
                      tuple -> {
                        long waitlistSize = tuple.getT1() != null ? tuple.getT1() : 0L;
                        long activeSize = tuple.getT2() != null ? tuple.getT2() : 0L;
                        if (waitlistSize == 0 && activeSize == 0) {
                          return redisTemplate
                              .opsForSet()
                              .remove("active_event_queues", eventId)
                              .then();
                        }
                        return Mono.empty();
                      });
            })
        .then();
  }
}
