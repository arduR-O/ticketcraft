package com.ticketcraft.booking.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeatResponse {
  private Long seatId;
  private String seatNumber;
  private String rowNumber;
  private String section;
  private BigDecimal price;
}
