package com.netcracker.core.declarative.service.composite;

import com.netcracker.core.declarative.client.k8s.ConfigMapClient;
import io.quarkus.runtime.Startup;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Coordinates composite structure watching based on ConfigMap ownership.
 * <p>
 * Periodically checks if the {@value #CONFIG_MAP_NAME} ConfigMap is managed by core-operator.
 * If managed, starts the {@link CompositeStructureRefChangeListener}; otherwise stops it.
 * This allows another operator to take over ConfigMap management when needed.
 * <p>
 * The feature can be disabled via {@code cloud.composite.structure.sync.enabled=false}.
 */
@ApplicationScoped
@Startup
@Slf4j
public class CompositeStructureWatchCoordinator {
    public static final String CONFIG_MAP_NAME = "composite-structure";

    private final CompositeStructureRefChangeListener compositeStructureRefChangeListener;
    private final ConfigMapClient configMapClient;
    private final String namespace;
    private final boolean featureEnabled;
    private final AtomicBoolean watcherRunning;

    @Inject
    public CompositeStructureWatchCoordinator(
            @ConfigProperty(name = "cloud.microservice.namespace") String namespace,
            @ConfigProperty(name = "cloud.composite.structure.sync.enabled", defaultValue = "true") boolean featureEnabled,
            CompositeStructureRefChangeListener compositeStructureRefChangeListener,
            ConfigMapClient configMapClient) {
        this.namespace = namespace;
        this.featureEnabled = featureEnabled;
        this.configMapClient = configMapClient;
        this.compositeStructureRefChangeListener = compositeStructureRefChangeListener;
        this.watcherRunning = new AtomicBoolean(false);
    }

    @Scheduled(every = "5m", concurrentExecution = Scheduled.ConcurrentExecution.SKIP, delayed = "0s")
    void ensureWatcherState() {
        if (!featureEnabled) {
            log.debug("Composite structure sync is disabled by configuration.");
            stopWatcher();
            return;
        }

        try {
            boolean shouldManage = configMapClient.shouldBeManagedByCoreOperator(CONFIG_MAP_NAME, namespace);
            if (shouldManage) {
                startWatcher();
            } else {
                log.info("Composite structure polling is disabled because '{}' is not managed by core-operator.", CONFIG_MAP_NAME);
                stopWatcher();
            }
        } catch (RuntimeException ex) {
            log.warn("Failed to verify management state for '{}'. Retrying on next schedule.", CONFIG_MAP_NAME, ex);
        }
    }

    @PreDestroy
    void stop() {
        stopWatcher();
    }

    private void startWatcher() {
        if (!watcherRunning.compareAndSet(false, true)) {
            return;
        }
        log.info("Starting composite structure watcher for ConfigMap '{}'", CONFIG_MAP_NAME);
        compositeStructureRefChangeListener.start();
    }

    private void stopWatcher() {
        if (!watcherRunning.compareAndSet(true, false)) {
            return;
        }
        compositeStructureRefChangeListener.stop();
    }
}
