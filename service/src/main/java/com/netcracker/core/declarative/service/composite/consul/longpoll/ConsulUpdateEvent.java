package com.netcracker.core.declarative.service.composite.consul.longpoll;

import com.netcracker.cloud.quarkus.consul.client.model.GetValue;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

/**
 * Base class for Consul KV update events.
 * <p>
 * Contains the list of key-value entries and the Consul modify index.
 */
@Getter
@AllArgsConstructor
@ToString
public abstract class ConsulUpdateEvent {

    private final List<GetValue> values;
    private final long consulIndex;
}
