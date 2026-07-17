package com.ticketcraft.booking.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SeatLockService {

  private final RedissonClient redissonClient;
  private final long lockLeaseMinutes;

  public SeatLockService(
      RedissonClient redissonClient,
      @Value("${booking.lock-lease-minutes:11}") long lockLeaseMinutes) {
    this.redissonClient = redissonClient;
    this.lockLeaseMinutes = lockLeaseMinutes;
  }

  /**
   * Attempts to acquire locks for all provided seat IDs. If any lock fails to acquire, releases all
   * currently acquired locks and returns false. The caller must sort the seatIds before calling this
   * to avoid deadlocks.
   *
   * @param sortedSeatIds The numerically sorted list of seat IDs to lock.
   * @return true if all locks were acquired, false otherwise.
   */
  public boolean acquireLocks(List<Long> sortedSeatIds) {
    List<RLock> acquiredLocks = new ArrayList<>();

    for (Long seatId : sortedSeatIds) {
      String lockKey = "lock:seat:" + seatId;
      RLock lock = redissonClient.getLock(lockKey);
      try {
        // Wait up to 1 second to acquire the lock, lease it for lockLeaseMinutes
        boolean isAcquired = lock.tryLock(1, lockLeaseMinutes, TimeUnit.MINUTES);
        if (isAcquired) {
          acquiredLocks.add(lock);
        } else {
          log.warn("Failed to acquire lock for seat {}", seatId);
          releaseLocks(acquiredLocks);
          return false;
        }
      } catch (InterruptedException e) {
        log.error("Thread interrupted while trying to acquire lock for seat {}", seatId, e);
        Thread.currentThread().interrupt();
        releaseLocks(acquiredLocks);
        return false;
      }
    }
    return true;
  }

  /**
   * Releases specific RLock instances.
   */
  public void releaseLocks(List<RLock> locks) {
    for (RLock lock : locks) {
      try {
        if (lock.isHeldByCurrentThread()) {
          lock.unlock();
        }
      } catch (Exception e) {
        log.error("Error releasing lock {}", lock.getName(), e);
      }
    }
  }

  /**
   * Releases locks by seat IDs.
   */
  public void releaseLocksByIds(List<Long> seatIds) {
    for (Long seatId : seatIds) {
      String lockKey = "lock:seat:" + seatId;
      RLock lock = redissonClient.getLock(lockKey);
      try {
        // Redisson handles unlocking safely if forceUnlock is used, or if the current thread holds it.
        // We use forceUnlock() in case this is called from a background scheduler thread
        // that did not originally acquire the lock.
        lock.forceUnlock();
      } catch (Exception e) {
        log.error("Error force releasing lock for seat {}", seatId, e);
      }
    }
  }
}
