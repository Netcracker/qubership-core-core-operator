package com.netcracker.core.declarative.service.composite.consul.longpoll;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handle for controlling a Consul watch operation.
 * <p>
 * Allows cancellation of an ongoing long-poll watch loop.
 */
//todo vlla переписать джавадок
public class LongPoolSession {
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    /**
     * Cancels the watch operation. Subsequent poll cycles will not be scheduled.
     */
    public void cancel() {
        cancelled.set(true);
    }

    /**
     * @return true if this watch has been cancelled
     */
    public boolean isCancelled() {
        return cancelled.get();
    }
}
