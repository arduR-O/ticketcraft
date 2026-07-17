package com.ticketcraft.booking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {
  private String status; // SUCCESS or FAILED
  private String transactionId;
  private String failureReason;
}
