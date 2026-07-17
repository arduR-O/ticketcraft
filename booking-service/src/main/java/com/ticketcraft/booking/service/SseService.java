package com.ticketcraft.booking.service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Service
public class SseService {

  private final ConcurrentHashMap<UUID, SseEmitter> emitters = new ConcurrentHashMap<>();

  public SseEmitter subscribe(UUID bookingId) {
    // 10 minute timeout (matches cart expiry)
    SseEmitter emitter = new SseEmitter(600_000L);
    emitters.put(bookingId, emitter);

    emitter.onCompletion(() -> emitters.remove(bookingId));
    emitter.onTimeout(() -> {
      emitter.complete();
      emitters.remove(bookingId);
    });
    emitter.onError((e) -> {
      log.error("SSE Error for booking {}: ", bookingId, e);
      emitters.remove(bookingId);
    });

    return emitter;
  }

  public void pushUpdate(UUID bookingId, Object data) {
    SseEmitter emitter = emitters.get(bookingId);
    if (emitter != null) {
      try {
        emitter.send(SseEmitter.event().name("booking-update").data(data));
      } catch (Exception e) {
        log.error("Failed to send SSE update for booking {}", bookingId, e);
        emitters.remove(bookingId);
      }
    }
  }

  public void complete(UUID bookingId) {
    SseEmitter emitter = emitters.remove(bookingId);
    if (emitter != null) {
      emitter.complete();
    }
  }
}
