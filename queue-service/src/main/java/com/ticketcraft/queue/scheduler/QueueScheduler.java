package com.ticketcraft.queue.scheduler;

import com.ticketcraft.queue.service.QueueService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled background task for orchestrating queue promotions.
 * 
 * Periodically triggers the QueueService to evaluate active event queues.
 * 
 * Abstracting this into a centralized scheduler ensures that promotions run reliably
 * across all active events in the background, fully decoupled from the lifecycle of
 * individual incoming HTTP requests.
 */
@Component
public class QueueScheduler {

  private final QueueService queueService;

  public QueueScheduler(QueueService queueService) {
    this.queueService = queueService;
  }

  /**
   * Triggers the promotion Lua script for all active event queues.
   * 
   * Runs every 5 seconds. Fetches the set of currently active events from Redis,
   * then calls the promoteUsers script for each one.
   * 
   * Fixed-rate polling (every 5 seconds) provides a good balance between responsiveness
   * (users move up the queue quickly) and preventing CPU/Redis overload. Using Spring's
   * @Scheduled with Reactor requires calling .subscribe() to actually initiate the async flow.
   */
  @Scheduled(fixedRate = 2000)
  public void promoteUsersInQueue() {
    queueService
        .getActiveEventQueues()
        .flatMap(eventId -> queueService.promoteUsers(eventId))
        .subscribe();
  }
}
