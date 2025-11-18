package com.netcracker.core.declarative.configuration;

import io.micrometer.core.instrument.config.MeterFilter;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

public class OperatorSdkMetricsFilter {

    @Produces
    @Singleton
    public MeterFilter disableOperatorSdkReconcileTimer() {
        return MeterFilter.denyNameStartsWith("operator.sdk.controllers.execution.reconcile");
    }
}