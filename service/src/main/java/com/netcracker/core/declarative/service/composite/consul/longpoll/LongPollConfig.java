package com.netcracker.core.declarative.service.composite.consul.longpoll;

import lombok.Builder;
import lombok.Value;

import java.time.Duration;

/**
 * Configuration for Consul long-polling: blocking query wait duration
 * and whether to emit the snapshot on the first successful poll.
 */
@Value
@Builder
public class LongPollConfig {
    @Builder.Default Duration wait = Duration.ofMinutes(9);
    @Builder.Default boolean fireOnFirstSuccess = true;
}
