package com.ticketcraft.catalog.repository;

import java.time.LocalDateTime;

public interface EventSummaryProjection {
  Long getId();
  String getTitle();
  String getDescription();
  LocalDateTime getDate();
  String getArtistName();
  String getVenueName();
  String getVenueLocation();
}
