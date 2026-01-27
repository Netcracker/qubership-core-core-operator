package com.netcracker.core.declarative.service.composite.consul;

import com.netcracker.cloud.quarkus.consul.client.model.GetValue;
import com.netcracker.core.declarative.service.composite.consul.longpoll.ConsulUpdateEvent;

import java.util.List;

/**
 * Event fired when the composite structure reference key changes in Consul.
 * <p>
 * The reference key points to the actual composite structure prefix path.
 */
public class CompositeStructureRefUpdateEvent extends ConsulUpdateEvent {

    public CompositeStructureRefUpdateEvent(List<GetValue> values, long consulIndex) {
        super(values, consulIndex);
    }
}
