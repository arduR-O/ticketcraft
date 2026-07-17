package com.ticketcraft.booking.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingCreatedEvent {
  private String bookingId;
  private String totalPrice;
  private String paymentToken;
  private String userId;
  private Long eventId;
  private String timestamp;
}
