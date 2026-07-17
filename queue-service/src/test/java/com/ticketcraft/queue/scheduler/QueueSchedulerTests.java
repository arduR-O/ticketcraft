package com.ticketcraft.queue.scheduler;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ticketcraft.queue.service.QueueService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
public class QueueSchedulerTests {

  @Mock private QueueService queueService;

  @InjectMocks private QueueScheduler queueScheduler;

  @Test
  void testPromoteUsersInQueue() {
    when(queueService.getActiveEventQueues()).thenReturn(Flux.just("1001", "1002"));
    when(queueService.promoteUsers(anyString())).thenReturn(Mono.empty());

    queueScheduler.promoteUsersInQueue();

    verify(queueService).promoteUsers("1001");
    verify(queueService).promoteUsers("1002");
  }
}
