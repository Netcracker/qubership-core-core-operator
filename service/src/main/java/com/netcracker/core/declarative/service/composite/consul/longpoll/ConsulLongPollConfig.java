package com.netcracker.core.declarative.service.composite.consul.longpoll;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration for Consul long-polling behavior.
 */
@ConfigMapping(prefix = "quarkus.consul-long-poll")
public interface ConsulLongPollConfig {

    /**
     * Delay (ms) before retrying after a failed poll or empty response.
     */
    @WithDefault("20000")
    int consulRetryTime();

    /**
     * Delay (ms) before the next poll after a successful response with data.
     */
    @WithDefault("3000")
    int consulOnSuccessDelayTime();
}
