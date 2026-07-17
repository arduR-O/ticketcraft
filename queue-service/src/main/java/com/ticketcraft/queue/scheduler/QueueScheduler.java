package com.ticketcraft.queue.scheduler;

import com.ticketcraft.queue.service.QueueService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class QueueScheduler {

  private final QueueService queueService;

  public QueueScheduler(QueueService queueService) {
    this.queueService = queueService;
  }

  @Scheduled(fixedRate = 5000)
  public void promoteUsersInQueue() {
    queueService
        .getActiveEventQueues()
        .flatMap(eventId -> queueService.promoteUsers(eventId))
        .subscribe();
  }
}
