package com.netcracker.core.declarative.service.composite.consul.longpoll2;

import com.netcracker.cloud.quarkus.consul.client.model.GetValue;

import java.util.List;

public interface ConsulUpdateEventFactory<T extends ConsulUpdateEvent> {
    T create(List<GetValue> values);
}
