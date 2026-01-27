package com.netcracker.core.declarative.service.composite.consul.longpoll2;

import com.netcracker.cloud.quarkus.consul.client.model.GetValue;

import java.util.List;

/**
 * Event fired when the composite structure data changes in Consul.
 * <p>
 * Contains the full snapshot of structure entries under the watched prefix.
 */
public class CompositeStructureConsulUpdateEvent extends ConsulUpdateEvent {

    public CompositeStructureConsulUpdateEvent(List<GetValue> values, long consulIndex) {
        super(values, consulIndex);
    }
}
