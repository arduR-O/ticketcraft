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

/**
 * Core service for the virtual waiting room.
 * 
 * What: Manages Redis ZSets (Sorted Sets) for waitlists and active sessions, 
 * issues JWT pass tokens, and executes Lua scripts for atomic queue promotions.
 * 
 * Why: High-demand ticketing requires strict fairness and rate limiting to protect
 * downstream services (Catalog, Booking). Redis ZSets provide O(log(N)) ranking,
 * ensuring users are processed in exactly the order they joined. Lua scripting
 * guarantees atomicity during the complex promotion and eviction sweeps.
 */
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

  /**
   * Generates a signed JWT for users who have been promoted to active.
   * 
   * What: Builds a short-lived (5 min) JWT containing the user ID and event ID.
   * 
   * Why: Prevents users from forging their way past the Gateway. The Gateway will
   * cryptographically verify this token's signature before allowing the user to hit
   * the downstream Booking or Catalog endpoints.
   * 
   * @param userId The ID of the user.
   * @param eventId The event ID.
   * @return The signed JWT string.
   */
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

  /**
   * Adds a user to the waitlist for a specific event.
   * 
   * What: Adds the event to the `active_event_queues` set (if not already present),
   * and pushes the user into the waitlist ZSet with the current timestamp as their score.
   * 
   * Why: Storing the timestamp as the score ensures First-In-First-Out (FIFO) ordering
   * when we rank the set later. Tracking `active_event_queues` allows the background
   * scheduler to know exactly which events need promotion sweeps without scanning all keys.
   * 
   * @param eventId The event ID.
   * @param userId The user ID.
   * @return The 0-based rank of the user in the waitlist.
   */
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
                return redisTemplate.opsForZSet().remove(getWaitlistKey(eventId), userId)
                    .then(Mono.just(generatePassToken(userId, eventId)));
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

  /**
   * Attempts to issue a queue pass immediately without making the user wait.
   * 
   * What: Checks if the waitlist is empty and the active sessions are below the maximum.
   * If both are true, the user is directly inserted into the active sessions and receives a pass.
   * 
   * Why: Prevents the overhead of SSE connections for events that have no active queue.
   */
  public Mono<String> attemptFastTrack(String eventId, String userId) {
    String waitlistKey = getWaitlistKey(eventId);
    String activeSessionsKey = getActiveSessionsKey(eventId);
    String heartbeatsKey = getHeartbeatsKey(eventId);
    long now = Instant.now().toEpochMilli();

    return redisTemplate.opsForZSet().score(activeSessionsKey, userId)
        .flatMap(score -> {
          if (score != null) {
            // User was already in active sessions, but they are requesting a new fast-track pass
            // This means they navigated away and came back.
            // According to requirements, they lose their spot and must queue again.
            return redisTemplate.opsForZSet().remove(activeSessionsKey, userId)
                .then(redisTemplate.opsForZSet().remove(heartbeatsKey, userId))
                .then(Mono.<String>empty());
          }
          return Mono.<String>empty();
        })
        .switchIfEmpty(
            redisTemplate.opsForZSet().size(waitlistKey).defaultIfEmpty(0L)
                .zipWith(redisTemplate.opsForZSet().size(activeSessionsKey).defaultIfEmpty(0L))
                .flatMap(tuple -> {
                  long waitlistSize = tuple.getT1();
                  long activeSize = tuple.getT2();

                  if (waitlistSize == 0 && activeSize < maxActiveSessions) {
                    return redisTemplate.opsForZSet().add(activeSessionsKey, userId, (double) now)
                        .then(redisTemplate.opsForZSet().add(heartbeatsKey, userId, (double) now))
                        .then(Mono.just(generatePassToken(userId, eventId)));
                  }
                  return Mono.empty();
                })
        );
  }

  public Flux<String> getActiveEventQueues() {
    return redisTemplate.opsForSet().members("active_event_queues");
  }

  /**
   * Periodically promotes users from the waitlist to active sessions.
   * 
   * What: Executes the `promote_users.lua` script which atomically evicts dead sessions
   * (missing heartbeats) and moves the top waitlisted users to the active set until it
   * reaches `maxActiveSessions`. Cleans up the event from `active_event_queues` if both
   * queues are completely empty.
   * 
   * Why: Using a Lua script prevents race conditions if multiple instances of QueueService
   * attempt to promote users simultaneously. It guarantees that exactly the right number
   * of users are promoted without exceeding the max active threshold, which protects the
   * database from sudden traffic spikes.
   * 
   * @param eventId The event ID to process.
   * @return A Mono signaling completion.
   */
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
