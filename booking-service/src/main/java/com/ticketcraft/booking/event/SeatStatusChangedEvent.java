package com.ticketcraft.booking.event;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatStatusChangedEvent {
  private Long eventId;
  private List<Long> seatIds;
  private String newStatus;
  private String bookingId;
  private String timestamp;
}
