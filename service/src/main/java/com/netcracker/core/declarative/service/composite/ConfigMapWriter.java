package com.netcracker.core.declarative.service.composite;

import com.netcracker.core.declarative.client.k8s.ConfigMapClient;
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
 * on failure, up to {@value #MAX_RETRY_ATTEMPTS} attempts.
 */
@ApplicationScoped
@Slf4j
//todo vlla действительно ли нам нужны ретраи для этой операции?
public class ConfigMapWriter {
    static final int MAX_RETRY_ATTEMPTS = 4;

    private final ConfigMapClient configMapClient;
    private final String namespace;

    @Inject
    public ConfigMapWriter(ConfigMapClient configMapClient,
                           @ConfigProperty(name = "cloud.microservice.namespace") String namespace) {
        this.configMapClient = configMapClient;
        this.namespace = namespace;
    }

    /**
     * Schedules an asynchronous ConfigMap update.
     */
    @Asynchronous
    @Retry(maxRetries = MAX_RETRY_ATTEMPTS, delay = 3000, maxDuration = 2, durationUnit = ChronoUnit.MINUTES)
    @ExponentialBackoff(maxDelay = 30, maxDelayUnit = ChronoUnit.SECONDS)
    public CompletionStage<Void> requestUpdate(String configMapName, Map<String, String> payload) {
        Objects.requireNonNull(configMapName, "configMapName");
        Objects.requireNonNull(payload, "payload");

        Map<String, String> snapshot = Map.copyOf(payload);

        try {
            configMapClient.createOrUpdate(configMapName, namespace, snapshot);
            log.debug("Successfully updated config map '{}'", configMapName);
            return CompletableFuture.completedFuture(null);
        } catch (RuntimeException ex) {
            log.warn("Failed to update config map '{}', will retry with backoff", configMapName, ex);
            throw ex;
        }
    }
}
