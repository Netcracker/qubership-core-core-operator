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
    T create(List<GetValue> values, long consulIndex);
}
