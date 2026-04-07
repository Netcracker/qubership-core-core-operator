package com.netcracker.core.declarative.service.composite.model;

import java.util.Arrays;

public enum CloudProvider {
    AKS("AKS"),
    GKE("GKE"),
    EKS("EKS"),
    ON_PREM("OnPrem");

    private final String value;

    CloudProvider(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static CloudProvider fromString(String value) {
        return Arrays.stream(values())
                .filter(cp -> cp.value.equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invalid CLOUD_PROVIDER value: '%s'. Must be one of: %s"
                                .formatted(value,
                                        Arrays.stream(values())
                                                .map(CloudProvider::getValue)
                                                .toList())
                ));
    }

    @Override
    public String toString() {
        return value;
    }
}