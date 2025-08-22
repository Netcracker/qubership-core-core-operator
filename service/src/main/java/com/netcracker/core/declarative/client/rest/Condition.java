package com.netcracker.core.declarative.client.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Condition(
        @JsonProperty("type")
        String type,
        @JsonProperty("state")
        ProcessStatus state,
        @JsonProperty
        String reason,
        @JsonProperty
        String message) {}