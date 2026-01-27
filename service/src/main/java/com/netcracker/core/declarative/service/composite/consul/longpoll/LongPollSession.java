package com.netcracker.core.declarative.service.composite.consul.longpoll;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A cancellable handle for an active Consul long-polling watch.
 * <p>
 * Thread-safe. Once cancelled, the associated poll loop will terminate
 * after completing its current cycle.
 *
 * @see ConsulLongPoller#startWatchConsulRoot
 */
public class LongPollSession {
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public void cancel() {
        cancelled.set(true);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }
}
