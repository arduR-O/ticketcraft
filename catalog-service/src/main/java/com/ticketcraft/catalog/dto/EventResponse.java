package com.ticketcraft.catalog.dto;

import java.time.LocalDateTime;

public record EventResponse(
    Long id,
    String title,
    String description,
    LocalDateTime date,
    String artistName,
    String venueName,
    String venueLocation) {}
