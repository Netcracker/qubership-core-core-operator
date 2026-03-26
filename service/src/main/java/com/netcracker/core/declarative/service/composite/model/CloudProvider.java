package com.netcracker.core.declarative.service.composite.model;

import java.util.Arrays;

public enum CloudProvider {
    AKS, GKE, EKS, OnPrem;

    public static CloudProvider fromString(String value) {
        return Arrays.stream(values())
                .filter(cp -> cp.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid CLOUD_PROVIDER value: '%s'. Must be one of: %s"
                        .formatted(value, Arrays.toString(values()))));
    }
}