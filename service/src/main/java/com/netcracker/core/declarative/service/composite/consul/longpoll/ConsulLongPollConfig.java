package com.netcracker.core.declarative.service.composite.consul.longpoll;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.consul-long-poll")
public interface ConsulLongPollConfig {
    String DEFAULT_CONSUL_RETRY_TIME = "20000";

    /**
     * Retry time if consul is not available
     */
    @WithDefault(DEFAULT_CONSUL_RETRY_TIME)
    Integer consulRetryTime();

    /**
     * Delay in milliseconds between successful Consul long polling attempts before scheduling the next poll.
     */
    @WithDefault("3000")
    int consulOnSuccessDelayTime();
}
