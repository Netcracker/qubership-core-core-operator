package com.netcracker.core.declarative.service.composite;

import com.netcracker.core.declarative.client.k8s.ConfigMapClient;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.smallrye.faulttolerance.api.ExponentialBackoff;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.Asynchronous;
import org.eclipse.microprofile.faulttolerance.Retry;

import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Asynchronously writes data to Kubernetes ConfigMaps with retry support.
 * <p>
 * Uses exponential backoff (3s -> 6s -> 12s -> 24s -> 30s max) for retries
 * on failure, up to 10 attempts.
 */
@ApplicationScoped
@Slf4j
public class ConfigMapWriter {
    private final ConfigMapClient configMapClient;
    private final String namespace;

    @Inject
    public ConfigMapWriter(ConfigMapClient configMapClient,
                           @ConfigProperty(name = "cloud.microservice.namespace") String namespace) {
        this.configMapClient = configMapClient;
        this.namespace = namespace;
    }

    @Asynchronous
    @Retry(maxRetries = 10, delay = 3000, maxDuration = 5, durationUnit = ChronoUnit.MINUTES)
    @ExponentialBackoff(maxDelay = 30, maxDelayUnit = ChronoUnit.SECONDS)
    public CompletionStage<Void> requestUpdate(String configMapName, Map<String, String> payload, HasMetadata owner) {
        Objects.requireNonNull(configMapName, "configMapName");
        Objects.requireNonNull(payload, "payload");

        try {
            configMapClient.createOrUpdate(configMapName, namespace, payload, owner);
            log.debug("Successfully updated config map '{}'", configMapName);
            return CompletableFuture.completedFuture(null);
        } catch (RuntimeException ex) {
            log.warn("Failed to update config map '{}', will retry with backoff", configMapName, ex);
            throw ex;
        }
    }
}
