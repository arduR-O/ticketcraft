package com.ticketcraft.catalog.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

public record EventDetailResponse(
    Long id,
    String title,
    String description,
    LocalDateTime date,
    String artistName,
    String venueName,
    String venueLocation,
    Double venueLatitude,
    Double venueLongitude,
    Long totalSeats,
    Long availableSeats,
    Map<String, BigDecimal> pricingTiers) {}
