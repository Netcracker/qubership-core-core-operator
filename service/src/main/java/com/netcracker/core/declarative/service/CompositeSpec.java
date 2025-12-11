package com.netcracker.core.declarative.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import static io.quarkus.runtime.util.StringUtil.isNullOrEmpty;

@Data
@NoArgsConstructor
@AllArgsConstructor
public final class CompositeSpec {
    private String controllerNamespace;
    private String originNamespace;
    private String peerNamespace;
    @JsonProperty("baseline")
    private CompositeSpecBaseline baseline;

    public void validate() {
        if (isNullOrEmpty(originNamespace)) {
            throw new IllegalArgumentException("Origin namespace cannot be null or empty: " + this);
        }
        if (!isNullOrEmpty(controllerNamespace) && isNullOrEmpty(peerNamespace)) {
            throw new IllegalArgumentException("BG domain missed value for peer namespace: " + this);
        }
        if (isNullOrEmpty(controllerNamespace) && !isNullOrEmpty(peerNamespace)) {
            throw new IllegalArgumentException("BG domain missed value for controller namespace: " + this);
        }

        // duplicate code above for baseline just for better error message formatting
        if (baseline != null) {
            if (isNullOrEmpty(baseline.originNamespace)) {
                throw new IllegalArgumentException("Baseline origin namespace cannot be null or empty: " + this);
            }
            if (!isNullOrEmpty(baseline.controllerNamespace) && isNullOrEmpty(baseline.peerNamespace)) {
                throw new IllegalArgumentException("Baseline BG domain missed value for peer namespace: " + this);
            }
            if (isNullOrEmpty(baseline.controllerNamespace) && !isNullOrEmpty(baseline.peerNamespace)) {
                throw new IllegalArgumentException("Baseline BG domain missed value for controller namespace: " + this);
            }
        }
    }

    @JsonIgnore
    public String getCompositeId() {
        if (!isBaseline()) {
            return baseline.originNamespace;
        } else if (!originNamespace.isEmpty()) {
            return originNamespace;
        }

        throw new IllegalArgumentException("Can't resolve composite id from spec: " + this);
    }

    @JsonIgnore
    public boolean isBaseline() {
        return baseline == null || isNullOrEmpty(baseline.originNamespace);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static final class CompositeSpecBaseline {
        private String controllerNamespace;
        private String originNamespace;
        private String peerNamespace;
    }
}