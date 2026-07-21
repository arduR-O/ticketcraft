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

/**
 * Controller for managing Server-Sent Events (SSE) connections for real-time seat map updates.
 * 
 * Manages long-lived HTTP connections, maintaining a map of connected clients per eventId.
 * 
 * We use SSE instead of WebSockets because seat status updates are strictly one-way (server to client).
 * SSE is natively supported by HTTP/1.1, simpler to scale through load balancers, and has built-in
 * reconnection logic in the browser's EventSource API.
 */
@RestController
@RequestMapping("/api/events")
public class SeatStreamController {

  private final ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>> emitters =
      new ConcurrentHashMap<>();

  /**
   * Subscribes a client to the seat update stream for a specific event.
   * 
   * Creates an SseEmitter, stores it in a thread-safe map, and sends a handshake event.
   * 
   * The handshake ensures the connection is fully established and prevents some reverse
   * proxies (like NGINX) from dropping idle connections immediately. We keep a 30-minute timeout
   * because typical user sessions in ticket buying last this long.
   * 
   * @param id The event ID to subscribe to.
   * @return The SseEmitter that keeps the connection open.
   */
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

  /**
   * Broadcasts a seat status update to all connected clients for a given event.
   * 
   * Iterates through the list of emitters for the eventId and sends the payload.
   * Cleans up any emitters that throw IOException (e.g. client closed the tab).
   * 
   * This method is called by the RedisMessageSubscriber when a Pub/Sub message is received.
   * Using ConcurrentHashMap and CopyOnWriteArrayList ensures we don't encounter
   * ConcurrentModificationExceptions while iterating over and mutating the active connections.
   * 
   * @param eventId The event ID.
   * @param updates The list of seat updates to broadcast.
   */
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
