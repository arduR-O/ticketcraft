package com.ticketcraft.booking.dto;

import java.util.List;
import lombok.Data;

@Data
public class BookingRequest {
  private Long eventId;
  private List<Long> seatIds;
}
