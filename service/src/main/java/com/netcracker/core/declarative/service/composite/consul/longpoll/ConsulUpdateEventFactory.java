package com.netcracker.core.declarative.service.composite.consul.longpoll;

import com.netcracker.cloud.quarkus.consul.client.model.GetValue;

import java.util.List;

/**
 * Factory for creating typed Consul update events.
 *
 * @param <T> the event type
 */
@FunctionalInterface
public interface ConsulUpdateEventFactory<T extends ConsulUpdateEvent> {

    /**
     * Creates an event from Consul KV response.
     *
     * @param values      the list of key-value entries from Consul
     * @param consulIndex the Consul modify index for tracking changes
     * @return the typed event
     */
    T create(List<GetValue> values, long consulIndex);
}
