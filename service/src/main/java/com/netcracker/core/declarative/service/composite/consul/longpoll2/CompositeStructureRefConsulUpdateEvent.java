package com.netcracker.core.declarative.service.composite.consul.longpoll2;

import com.netcracker.cloud.quarkus.consul.client.model.GetValue;

import java.util.List;

public class CompositeStructureRefConsulUpdateEvent extends ConsulUpdateEvent {
    public CompositeStructureRefConsulUpdateEvent(List<GetValue> values) {
        super(values);
    }
}
