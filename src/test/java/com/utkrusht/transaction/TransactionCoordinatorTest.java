package com.utkrusht.transaction;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class TransactionCoordinatorTest {
    @Test
    void testNoDeadlockUnderConcurrentTransactions() throws InterruptedException, ExecutionException {
        LockRegistry registry = new LockRegistry();
        TransactionCoordinator coordinator = new TransactionCoordinator(registry);
        ServiceTransactionSimulator simulator = new ServiceTransactionSimulator(coordinator);
        int concurrentRuns = 10;
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();

        // Launch concurrent pairs of A/B transactions
        for (int i = 0; i < concurrentRuns; ++i) {
            futures.add(simulator.serviceATransaction());
            futures.add(simulator.serviceBTransaction());
        }

        CompletableFuture<Void> allComplete = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
        );
        allComplete.get(); // Wait for all

        // Assert no deadlocks and at least one succeeds per pair
        for (int i = 0; i < futures.size(); i += 2) {
            boolean a = futures.get(i).get();
            boolean b = futures.get(i+1).get();
            assertTrue(a || b, "At least one of A/B should succeed");
        }
    }

    @Test
    void testTransactionRetriesIfContention() throws Exception {
        LockRegistry registry = new LockRegistry();
        TransactionCoordinator coordinator = new TransactionCoordinator(registry);

        // Take the lock manually to simulate contention
        var lock = registry.getLock("resource-1");
        lock.lock();
        try {
            CompletableFuture<Boolean> future = coordinator.executeTransaction(
                    List.of("resource-1", "resource-2"),
                    () -> { /* no-op */ }
            );
            // Should eventually fail because lock is not released during transaction window
            assertFalse(future.get());
        } finally {
            lock.unlock();
        }
    }

    @Test
    void testLocksAlwaysReleased() throws Exception {
        LockRegistry registry = new LockRegistry();
        TransactionCoordinator coordinator = new TransactionCoordinator(registry);

        CompletableFuture<Boolean> f = coordinator.executeTransaction(List.of("resource-3"), () -> {
            throw new RuntimeException("force failure");
        });
        assertFalse(f.get());
        // Lock is reacquirable
        var lock = registry.getLock("resource-3");
        assertTrue(lock.tryLock());
        lock.unlock();
    }
}
