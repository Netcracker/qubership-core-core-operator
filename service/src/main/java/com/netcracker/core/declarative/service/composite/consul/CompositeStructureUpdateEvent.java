package com.netcracker.core.declarative.service.composite.consul;

import com.netcracker.cloud.quarkus.consul.client.model.GetValue;
import com.netcracker.core.declarative.service.composite.consul.longpoll.ConsulUpdateEvent;

import java.util.List;

/**
 * Event fired when the composite structure data changes in Consul.
 * <p>
 * Contains the full snapshot of structure entries under the watched prefix.
 */
public class CompositeStructureUpdateEvent extends ConsulUpdateEvent {

    public CompositeStructureUpdateEvent(List<GetValue> values, long consulIndex) {
        super(values, consulIndex);
    }
}
