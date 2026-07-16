package com.ticketcraft.queue.service;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class QueueService {

  private final ReactiveRedisTemplate<String, String> redisTemplate;

  // Using hash tags to guarantee atomicity on the same cluster node
  private static final String WAITLIST_KEY = "{event_1001}:waitlist";
  private static final String ACTIVE_SESSIONS_KEY = "{event_1001}:active_sessions";
  private static final int MAX_ACTIVE_SESSIONS = 10000;

  public QueueService(ReactiveRedisTemplate<String, String> redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  public Mono<Long> enqueueUser(String userId) {
    double score = Instant.now().toEpochMilli();
    return redisTemplate
        .opsForZSet()
        .add(WAITLIST_KEY, userId, score)
        .then(redisTemplate.opsForZSet().rank(WAITLIST_KEY, userId));
  }

  public Mono<Long> getQueuePosition(String userId) {
    return redisTemplate.opsForZSet().rank(WAITLIST_KEY, userId);
  }

  public Mono<String> getPromotionPass(String userId) {
    return redisTemplate
        .opsForZSet()
        .score(ACTIVE_SESSIONS_KEY, userId)
        .flatMap(
            score -> {
              if (score != null) {
                return Mono.just("pass_" + UUID.nameUUIDFromBytes(userId.getBytes()).toString());
              }
              return Mono.empty();
            });
  }

  public Mono<Void> promoteUsers() {
    return redisTemplate
        .opsForZSet()
        .size(ACTIVE_SESSIONS_KEY)
        .flatMap(
            activeCount -> {
              long availableSlots = MAX_ACTIVE_SESSIONS - (activeCount != null ? activeCount : 0L);
              if (availableSlots > 0) {
                return redisTemplate
                    .opsForZSet()
                    .popMin(WAITLIST_KEY, availableSlots)
                    .collectList()
                    .flatMap(
                        tuples -> {
                          if (tuples.isEmpty()) return Mono.empty();
                          return Mono.when(
                              tuples.stream()
                                  .map(
                                      tuple ->
                                          redisTemplate
                                              .opsForZSet()
                                              .add(
                                                  ACTIVE_SESSIONS_KEY,
                                                  tuple.getValue(),
                                                  Instant.now().toEpochMilli()))
                                  .toList());
                        });
              }
              return Mono.empty();
            });
  }
}
