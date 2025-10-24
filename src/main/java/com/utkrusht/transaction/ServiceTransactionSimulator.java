package com.utkrusht.transaction;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

public class ServiceTransactionSimulator {
    private final TransactionCoordinator coordinator;
    public ServiceTransactionSimulator(TransactionCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    /**
     * Simulate ServiceA: lock resource1 then resource2 (but transaction coordinator will order).
     */
    public CompletableFuture<Boolean> serviceATransaction() {
        return coordinator.executeTransaction(
                Arrays.asList("resource-1", "resource-2"),
                () -> simulateTransactionalOperation("ServiceA")
        );
    }

    /**
     * Simulate ServiceB: lock resource2 then resource1 (but coordinator will order).
     */
    public CompletableFuture<Boolean> serviceBTransaction() {
        return coordinator.executeTransaction(
                Arrays.asList("resource-2", "resource-1"),
                () -> simulateTransactionalOperation("ServiceB")
        );
    }

    private void simulateTransactionalOperation(String serviceName) {
        // Simulating transaction logic
        try {
            System.out.printf("[%s] Performing transactional operation...\n", serviceName);
            Thread.sleep(100); // simulate some work
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
