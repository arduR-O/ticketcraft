package com.ticketcraft.booking.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service for acquiring and releasing distributed locks for seats.
 * 
 * What: Uses Redisson to acquire Redis-based locks for specific seat IDs.
 * 
 * Why: A distributed lock ensures that if two users attempt to add the exact same seat
 * to their carts concurrently across different instances of booking-service, only one
 * will succeed. This prevents overselling before the final checkout stage. Redis is
 * used because of its speed and shared state across horizontal replicas.
 */
@Slf4j
@Service
public class SeatLockService {

  private final RedissonClient redissonClient;
  private final long lockLeaseMinutes;
  private final long lockWaitSeconds;

  public SeatLockService(
      RedissonClient redissonClient,
      @Value("${booking.lock-lease-minutes:11}") long lockLeaseMinutes,
      @Value("${booking.lock-wait-seconds:2}") long lockWaitSeconds) {
    this.redissonClient = redissonClient;
    this.lockLeaseMinutes = lockLeaseMinutes;
    this.lockWaitSeconds = lockWaitSeconds;
  }

  /**
   * Attempts to acquire distributed locks for a list of seat IDs.
   * 
   * What: Iterates through the provided seat IDs and tries to acquire a Redisson lock with a
   * configured wait time and 10-minute lease time. If any lock fails to acquire, it releases
   * all previously acquired locks in the batch and returns false.
   * 
   * Why: We need all-or-nothing locking for a cart. If a user wants 4 seats but only 3 are
   * available, the entire operation should fail. The 10-minute lease time aligns with the
   * cart expiration window, ensuring locks automatically drop if the server crashes.
   *
   * @param seatIds List of seat IDs to lock. Must be sorted to prevent deadlocks.
   * @return true if all locks were acquired, false otherwise.
   */
  public boolean acquireLocks(List<Long> sortedSeatIds) {
    List<RLock> acquiredLocks = new ArrayList<>();

    for (Long seatId : sortedSeatIds) {
      String lockKey = "lock:seat:" + seatId;
      RLock lock = redissonClient.getLock(lockKey);
      try {
        // Wait up to lockWaitSeconds to acquire the lock, lease it for lockLeaseMinutes
        boolean isAcquired = lock.tryLock(lockWaitSeconds, lockLeaseMinutes * 60, TimeUnit.SECONDS);
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
   * 
   * What: Unlocks the provided Redisson locks if the current thread holds them.
   * 
   * Why: Used as a rollback mechanism during the locking phase if a partial acquisition fails,
   * ensuring we don't hold hostage seats that weren't fully booked.
   * 
   * @param locks The list of RLock instances to release.
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
   * 
   * What: Looks up the lock by seat ID and forcibly unlocks it, even if the current thread
   * doesn't own the lock.
   * 
   * Why: Used by the expiration scheduler (which runs on a background thread) or during
   * checkout completion/failure. Because the original thread that acquired the lock may
   * no longer exist or be processing this request, we must use `forceUnlock()` to clear it.
   * 
   * @param seatIds The list of seat IDs to release.
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
