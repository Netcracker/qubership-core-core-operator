package com.netcracker.core.declarative.service.composite.consul.longpoll;

import com.netcracker.core.declarative.service.composite.consul.model.ConsulPrefixSnapshot;

/**
 * Callback for handling Consul long-poll results — either a successful snapshot or an error.
 */
public interface PollResultHandler {
    void onSuccess(ConsulPrefixSnapshot keyValueList);
    void onError(Throwable throwable);
}
