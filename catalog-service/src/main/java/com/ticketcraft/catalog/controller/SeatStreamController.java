package com.ticketcraft.catalog.controller;

import com.ticketcraft.catalog.dto.SeatStatusUpdate;
import com.ticketcraft.catalog.dto.SeatStreamPayload;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/events")
public class SeatStreamController {

  private final ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>> emitters =
      new ConcurrentHashMap<>();

  @GetMapping(value = "/{id}/seat-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter streamSeatUpdates(@PathVariable("id") Long id) {
    // 30 minutes timeout (1,800,000 milliseconds)
    SseEmitter emitter = new SseEmitter(1800000L);

    emitters.computeIfAbsent(id, k -> new CopyOnWriteArrayList<>()).add(emitter);

    emitter.onCompletion(() -> removeEmitter(id, emitter));
    emitter.onTimeout(() -> removeEmitter(id, emitter));
    emitter.onError(e -> removeEmitter(id, emitter));

    // Send an initial handshake event to prevent client timeouts
    try {
      emitter.send(SseEmitter.event().name("handshake").data("connected"));
    } catch (IOException e) {
      removeEmitter(id, emitter);
    }

    return emitter;
  }

  private void removeEmitter(Long eventId, SseEmitter emitter) {
    CopyOnWriteArrayList<SseEmitter> list = emitters.get(eventId);
    if (list != null) {
      list.remove(emitter);
      if (list.isEmpty()) {
        emitters.remove(eventId);
      }
    }
  }

  public void broadcast(Long eventId, List<SeatStatusUpdate> updates) {
    CopyOnWriteArrayList<SseEmitter> list = emitters.get(eventId);
    if (list == null || list.isEmpty()) {
      return;
    }

    SeatStreamPayload payload = new SeatStreamPayload(eventId, updates, Instant.now());
    List<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();

    for (SseEmitter emitter : list) {
      try {
        emitter.send(
            SseEmitter.event().name("seat-update").data(payload, MediaType.APPLICATION_JSON));
      } catch (Exception e) {
        deadEmitters.add(emitter);
      }
    }

    for (SseEmitter dead : deadEmitters) {
      dead.complete();
      removeEmitter(eventId, dead);
    }
  }
}
