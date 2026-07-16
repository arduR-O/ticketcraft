package com.ticketcraft.catalog.dto;

import com.ticketcraft.catalog.model.SeatCategory;
import com.ticketcraft.catalog.model.SeatStatus;
import java.math.BigDecimal;

public record SeatResponse(
    Long id,
    String seatNumber,
    String rowNumber,
    String section,
    Integer xCoordinate,
    Integer yCoordinate,
    SeatCategory category,
    SeatStatus status,
    BigDecimal price,
    Long eventId
) {}
