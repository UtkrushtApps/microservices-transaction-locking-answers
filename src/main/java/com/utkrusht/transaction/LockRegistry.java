package com.utkrusht.transaction;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class LockRegistry {
    private final ConcurrentHashMap<String, ReentrantLock> lockMap = new ConcurrentHashMap<>();

    /**
     * Get or create lock for the resourceId.
     */
    public ReentrantLock getLock(String resourceId) {
        return lockMap.computeIfAbsent(resourceId, id -> new ReentrantLock());
    }
}
