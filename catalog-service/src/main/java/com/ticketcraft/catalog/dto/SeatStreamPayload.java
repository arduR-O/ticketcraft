package com.ticketcraft.catalog.dto;

import java.time.Instant;
import java.util.List;

public record SeatStreamPayload(Long eventId, List<SeatStatusUpdate> updates, Instant timestamp) {}
