package com.ticketcraft.booking.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

@ExtendWith(MockitoExtension.class)
class SeatLockServiceTests {

  @Mock private RedissonClient redissonClient;

  private SeatLockService seatLockService;

  @BeforeEach
  void setUp() {
    seatLockService = new SeatLockService(redissonClient, 11L, 2L);
  }

  @Test
  void testAcquireLocks_Success() throws InterruptedException {
    List<Long> seatIds = Arrays.asList(101L, 102L);
    RLock mockLock1 = mock(RLock.class);
    RLock mockLock2 = mock(RLock.class);

    when(redissonClient.getLock("lock:seat:101")).thenReturn(mockLock1);
    when(redissonClient.getLock("lock:seat:102")).thenReturn(mockLock2);

    when(mockLock1.tryLock(2L, 11L * 60, TimeUnit.SECONDS)).thenReturn(true);
    when(mockLock2.tryLock(2L, 11L * 60, TimeUnit.SECONDS)).thenReturn(true);

    boolean acquired = seatLockService.acquireLocks(seatIds);

    assertTrue(acquired);
    verify(mockLock1).tryLock(2L, 11L * 60, TimeUnit.SECONDS);
    verify(mockLock2).tryLock(2L, 11L * 60, TimeUnit.SECONDS);
  }

  @Test
  void testAcquireLocks_FailureRollback() throws InterruptedException {
    List<Long> seatIds = Arrays.asList(101L, 102L);
    RLock mockLock1 = mock(RLock.class);
    RLock mockLock2 = mock(RLock.class);

    when(redissonClient.getLock("lock:seat:101")).thenReturn(mockLock1);
    when(redissonClient.getLock("lock:seat:102")).thenReturn(mockLock2);

    // Lock 1 acquired, Lock 2 fails
    when(mockLock1.tryLock(2L, 11L * 60, TimeUnit.SECONDS)).thenReturn(true);
    when(mockLock2.tryLock(2L, 11L * 60, TimeUnit.SECONDS)).thenReturn(false);

    when(mockLock1.isHeldByCurrentThread()).thenReturn(true);

    boolean acquired = seatLockService.acquireLocks(seatIds);

    assertFalse(acquired);
    verify(mockLock1).tryLock(2L, 11L * 60, TimeUnit.SECONDS);
    verify(mockLock2).tryLock(2L, 11L * 60, TimeUnit.SECONDS);
    // Verify rollback (unlocking lock 1)
    verify(mockLock1).unlock();
    verify(mockLock2, times(0)).unlock();
  }

  @Test
  void testReleaseLocksByIds() {
    List<Long> seatIds = Arrays.asList(101L, 102L);
    RLock mockLock1 = mock(RLock.class);
    RLock mockLock2 = mock(RLock.class);

    when(redissonClient.getLock("lock:seat:101")).thenReturn(mockLock1);
    when(redissonClient.getLock("lock:seat:102")).thenReturn(mockLock2);

    seatLockService.releaseLocksByIds(seatIds);

    verify(mockLock1).forceUnlock();
    verify(mockLock2).forceUnlock();
  }
}
