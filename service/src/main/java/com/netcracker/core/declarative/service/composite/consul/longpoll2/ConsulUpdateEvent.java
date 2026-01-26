package com.netcracker.core.declarative.service.composite.consul.longpoll2;

import com.netcracker.cloud.quarkus.consul.client.model.GetValue;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

@Getter
@AllArgsConstructor
@ToString
public abstract class ConsulUpdateEvent {
    private final List<GetValue> values;
}
