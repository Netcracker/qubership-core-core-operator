package com.netcracker.core.declarative.resources.base;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public enum Phase {
    @JsonProperty("Updated")
    UPDATED_PHASE("Updated"),
    @JsonProperty("BackingOff")
    BACKING_OFF("BackingOff"),
    @JsonProperty("InvalidConfiguration")
    INVALID_CONFIGURATION("InvalidConfiguration"),
    @JsonProperty("WaitingForDependency")
    WAITING_FOR_DEPENDS("WaitingForDependency"),
    @JsonProperty("Updating")
    UPDATING("Updating"),
    @JsonProperty("Unknown")
    UNKNOWN("Unknown");

    final String value;

    Phase(String value) {
        this.value = value;
    }
}
