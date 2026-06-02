package com.netcracker.core.declarative.service;

import com.netcracker.core.declarative.service.composite.model.CloudProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;

/**
 * Resolves the effective {@link CloudProvider}: the explicitly configured {@code CLOUD_PROVIDER}
 * value takes precedence; otherwise it is auto-detected via {@link CloudProviderDetector}.
 * <p>
 */
@ApplicationScoped
public class CloudProviderResolver {

    private final CloudProvider cloudProvider;

    @Inject
    public CloudProviderResolver(@ConfigProperty(name = "CLOUD_PROVIDER") Optional<String> configured,
                                 CloudProviderDetector cloudProviderDetector) {
        this.cloudProvider = configured
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(CloudProvider::fromString)
                .orElseGet(cloudProviderDetector::getCloudProvider);
    }

    public CloudProvider get() {
        return cloudProvider;
    }
}
