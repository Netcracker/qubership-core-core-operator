package org.qubership.core.declarative.service;

import io.vertx.ext.consul.ConsulClient;

public interface ConsulClientFactory {
        ConsulClient create(String token);
    }