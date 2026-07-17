package com.ticketcraft.booking.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentProcessedEvent {
  private String bookingId;
  private String status;
  private String transactionId;
  private String failureReason;
  private String timestamp;
}
