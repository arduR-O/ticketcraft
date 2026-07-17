package com.ticketcraft.catalog.dto;

import com.ticketcraft.catalog.model.SeatStatus;
import java.time.Instant;
import java.util.List;

public record SeatStatusEvent(
    Long eventId, List<Long> seatIds, SeatStatus newStatus, String bookingId, Instant timestamp) {}
