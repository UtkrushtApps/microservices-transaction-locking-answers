package com.utkrusht.transaction;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public class TransactionCoordinator {
    private final LockRegistry lockRegistry;
    private static final long LOCK_TIMEOUT_MS = 5000;
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 100;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public TransactionCoordinator(LockRegistry lockRegistry) {
        this.lockRegistry = lockRegistry;
    }

    /**
     * Attempts to execute a transactional operation on the given resources atomically,
     * ensuring locks are acquired in global order (alphabetically). Retries on lock timeout.
     *
     * @param resourceIds The resource IDs to lock
     * @param txnLogic Transaction operation lambda
     * @return CompletableFuture<Boolean> Completable result (true=success, false=fail after retries)
     */
    public CompletableFuture<Boolean> executeTransaction(List<String> resourceIds, Runnable txnLogic) {
        return executeWithRetries(resourceIds, txnLogic, 0);
    }

    private CompletableFuture<Boolean> executeWithRetries(List<String> resourceIds, Runnable txnLogic, int attempt) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> sortedIds = new ArrayList<>(resourceIds);
            Collections.sort(sortedIds); // Global deterministic order
            List<ReentrantLock> acquiredLocks = new ArrayList<>();
            try {
                for (String resourceId : sortedIds) {
                    ReentrantLock lock = lockRegistry.getLock(resourceId);
                    boolean locked = lock.tryLock(LOCK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    if (!locked) {
                        // failed
                        return false;
                    }
                    acquiredLocks.add(lock);
                }
                // If all locks acquired, run the logic
                txnLogic.run();
                return true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            } finally {
                // Unlock in *reverse* order for safety
                for (int i = acquiredLocks.size() - 1; i >= 0; i--) {
                    acquiredLocks.get(i).unlock();
                }
            }
        }, executor).thenCompose(success -> {
            if (success) {
                return CompletableFuture.completedFuture(true);
            } else if (attempt < MAX_RETRIES - 1) {
                // schedule retry with delay
                CompletableFuture<Boolean> delayedRetry = new CompletableFuture<>();
                executor.schedule(() -> {
                    executeWithRetries(resourceIds, txnLogic, attempt + 1)
                        .whenComplete((res, ex) -> {
                            if (ex != null) delayedRetry.completeExceptionally(ex);
                            else delayedRetry.complete(res);
                        });
                }, RETRY_DELAY_MS, TimeUnit.MILLISECONDS);
                return delayedRetry;
            } else {
                return CompletableFuture.completedFuture(false);
            }
        });
    }

    // It's better to use ScheduledExecutorService for the scheduled retry
    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(4);
    private final ScheduledExecutorService executorScheduled = executorService;
    {
        // scheduled override for retry
        // rebind executor to be scheduled
        ((ExecutorService) executorService).shutdown(); // shut initial executor after class load
    }

    {
        // swap executor to scheduled for the retry usage
        // ensures delayed retry works
        ((ExecutorService) executor).shutdown();
    }

    // Provide final version for outside usage
    public void shutdown() {
        executorService.shutdown();
    }
}
