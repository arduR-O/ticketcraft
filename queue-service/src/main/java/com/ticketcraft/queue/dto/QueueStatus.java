package com.ticketcraft.queue.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QueueStatus {
  private String status;
  private Long position;
  private String passToken;
}
