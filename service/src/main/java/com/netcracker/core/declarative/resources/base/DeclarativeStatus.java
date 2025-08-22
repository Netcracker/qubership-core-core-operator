package com.netcracker.core.declarative.resources.base;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.netcracker.core.declarative.resources.base.serializer.DeclarativeStatusDeserializer;
import com.netcracker.core.declarative.resources.base.serializer.DeclarativeStatusSerializer;
import io.javaoperatorsdk.operator.api.ObservedGenerationAwareStatus;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Setter
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeclarativeStatus extends ObservedGenerationAwareStatus {
    Phase phase = Phase.UNKNOWN;
    @JsonSerialize(using = DeclarativeStatusSerializer.class)
    @JsonDeserialize(using = DeclarativeStatusDeserializer.class)
    Map<String, CoreCondition> conditions = new LinkedHashMap<>();
    String requestId;
    String trackingId;
    private Map<String, Object> additionalProperties = new HashMap<>();

    @JsonIgnore
    public boolean isUpdated() {
        return Phase.UPDATED_PHASE.equals(phase);
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }
}
