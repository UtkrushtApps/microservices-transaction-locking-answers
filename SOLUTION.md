# Solution Steps

1. Implement a LockRegistry class that provides a ReentrantLock for each resourceId via a ConcurrentHashMap, supporting thread-safe access for all services.

2. In TransactionCoordinator, ensure all resourceIds are sorted alphabetically before acquiring locks to enforce a global acquisition order and prevent deadlock.

3. For each transaction, attempt to acquire each lock with tryLock and a 5-second timeout; if any fail, promptly release all successfully acquired locks.

4. If a lock could not be acquired, abort the transaction and, using CompletableFuture, automatically retry up to three times (for transient contention), waiting 100ms between attempts using a scheduled executor.

5. Ensure that all transaction logic and lock releases are performed asynchronously via CompletableFuture and a thread pool, never blocking main threads.

6. Release all locks in reverse order of acquisition in a finally-block to guarantee no resource leak, even if transaction logic throws an exception.

7. Provide a shutdown method in the coordinator to cleanly close ExecutorServices when the app finishes.

8. Write integration-style and unit-style tests to simulate ServiceA and ServiceB running concurrently with crossing dependency orders to verify no deadlock, successful retries, and proper lock release after transaction completion or failure.

