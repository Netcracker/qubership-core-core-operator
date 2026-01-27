package com.netcracker.core.declarative.service.composite.consul.longpoll2;

import com.netcracker.cloud.quarkus.consul.client.model.GetValue;

import java.util.List;

/**
 * Event fired when the composite structure reference key changes in Consul.
 * <p>
 * The reference key points to the actual composite structure prefix path.
 */
public class CompositeStructureRefConsulUpdateEvent extends ConsulUpdateEvent {

    public CompositeStructureRefConsulUpdateEvent(List<GetValue> values, long consulIndex) {
        super(values, consulIndex);
    }
}
