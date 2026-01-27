package com.netcracker.core.declarative.service.composite.consul.longpoll2;

/**
 * Handle for controlling a Consul watch operation.
 * <p>
 * Allows cancellation of an ongoing long-poll watch loop.
 */
public interface WatchHandle {

    /**
     * Cancels the watch operation. Subsequent poll cycles will not be scheduled.
     */
    void cancel();

    /**
     * @return true if this watch has been cancelled
     */
    boolean isCancelled();
}
