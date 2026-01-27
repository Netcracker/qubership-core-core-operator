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
 * If managed, starts the {@link CompositeStructureWatcher}; otherwise stops it.
 * This allows another operator to take over ConfigMap management when needed.
 */
@ApplicationScoped
@Startup
@Slf4j
public class CompositeStructureWatchCoordinator {
    public static final String CONFIG_MAP_NAME = "composite-structure";

    private final CompositeStructureWatcher compositeStructureWatcher;
    private final ConfigMapClient configMapClient;
    private final String namespace;
    private final AtomicBoolean watcherRunning;

    @Inject
    public CompositeStructureWatchCoordinator(
            @ConfigProperty(name = "cloud.microservice.namespace") String namespace,
            CompositeStructureWatcher compositeStructureWatcher,
            ConfigMapClient configMapClient) {
        this.namespace = namespace;
        this.configMapClient = configMapClient;
        this.compositeStructureWatcher = compositeStructureWatcher;
        this.watcherRunning = new AtomicBoolean(false);
    }

    /**
     * Periodically checks ConfigMap management status every 5 minutes.
     */
    @Scheduled(every = "5m", concurrentExecution = Scheduled.ConcurrentExecution.SKIP, delayed = "0s")
    void ensureWatcherState() {
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
        compositeStructureWatcher.start();
    }

    private void stopWatcher() {
        if (!watcherRunning.compareAndSet(true, false)) {
            return;
        }
        compositeStructureWatcher.stop();
    }
}
