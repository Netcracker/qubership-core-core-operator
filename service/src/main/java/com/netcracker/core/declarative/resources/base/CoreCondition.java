package com.netcracker.core.declarative.resources.base;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.netcracker.core.declarative.client.rest.ProcessStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Setter
@Getter
public class CoreCondition {
    @JsonProperty
    String lastTransitionTime;
    @JsonProperty
    String lastUpdateTime;
    @JsonProperty
    String message;
    @JsonProperty
    String reason;
    @JsonProperty
    ProcessStatus state;
    @JsonProperty
    Boolean status;
    @JsonProperty
    String type;

    @JsonCreator
    public CoreCondition(
            @JsonProperty("lastTransitionTime") String lastTransitionTime,
            @JsonProperty("lastUpdateTime") String lastUpdateTime,
            @JsonProperty("message") String message,
            @JsonProperty("reason") String reason,
            @JsonProperty("state") ProcessStatus state,
            @JsonProperty("status") Boolean status,
            @JsonProperty("type") String type) {
        this.lastTransitionTime = lastTransitionTime;
        this.lastUpdateTime = lastUpdateTime;
        this.message = message;
        this.reason = reason;
        this.state = state;
        this.status = status;
        this.type = type;
    }
}
