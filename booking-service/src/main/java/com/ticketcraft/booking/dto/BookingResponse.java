package com.ticketcraft.booking.dto;

import com.ticketcraft.booking.model.BookingStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponse {
  private UUID bookingId;
  private Long eventId;
  private BookingStatus status;
  private List<SeatResponse> seats;
  private BigDecimal totalPrice;
  private LocalDateTime createdAt;
  private LocalDateTime expiresAt;
}
