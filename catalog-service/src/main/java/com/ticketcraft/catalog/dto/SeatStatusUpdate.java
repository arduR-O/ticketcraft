package com.ticketcraft.catalog.dto;

import com.ticketcraft.catalog.model.SeatStatus;

public record SeatStatusUpdate(Long seatId, SeatStatus newStatus) {}
