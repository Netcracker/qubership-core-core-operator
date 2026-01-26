package com.netcracker.core.declarative.service.composite.consul.longpoll2;

import com.netcracker.cloud.quarkus.consul.client.model.GetValue;

import java.util.List;

public class CompositeStructureConsulUpdateEvent extends ConsulUpdateEvent {
    public CompositeStructureConsulUpdateEvent(List<GetValue> values) {
        super(values);
    }
}
