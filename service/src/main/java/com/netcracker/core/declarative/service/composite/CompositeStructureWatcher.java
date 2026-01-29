package com.netcracker.core.declarative.service.composite;

import com.netcracker.core.declarative.client.k8s.ConfigMapClient;
import com.netcracker.core.declarative.service.composite.consul.CompositeStructureUpdateEvent;
import com.netcracker.core.declarative.service.composite.consul.longpoll.ConsulLongPoller;
import com.netcracker.core.declarative.service.composite.consul.longpoll.LongPollSession;
import io.quarkus.scheduler.Scheduler;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Watches Consul for composite structure changes using long-polling.
 * <p>
 * Periodically checks if the {@value #CONFIG_MAP_NAME} ConfigMap is managed by core-operator.
 * If managed, starts watching the Consul path {@code composite/{compositeId}/structure};
 * otherwise stops watching. This allows another operator to take over ConfigMap management when needed.
 * <p>
 * Structure updates are handled by {@link CompositeStructureChangeListener}.
 * <p>
 * The feature can be disabled via {@code cloud.composite.structure.sync.enabled=false}.
 * <p>
 * Call {@link #start(String)} to begin watching. This schedules periodic ownership checks.
 */
@Singleton
@Slf4j
public class CompositeStructureWatcher {
    public static final String CONFIG_MAP_NAME = "composite-structure";
    private static final String JOB_IDENTITY = "composite-structure-watcher";
    private static final String CHECK_INTERVAL = "5m";
    private static final String COMPOSITE_STRUCTURE_KEY_TEMPLATE = "composite/%s/structure";

    private final ConsulLongPoller consulLongPoller;
    private final ConfigMapClient configMapClient;
    private final Scheduler scheduler;
    private final String namespace;
    private final boolean featureEnabled;

    private LongPollSession longPollSession;
    private boolean started;

    @Inject
    public CompositeStructureWatcher(
            @ConfigProperty(name = "cloud.microservice.namespace") String namespace,
            @ConfigProperty(name = "cloud.composite.structure.sync.enabled", defaultValue = "true") boolean featureEnabled,
            ConsulLongPoller consulLongPoller,
            ConfigMapClient configMapClient,
            Scheduler scheduler) {
        this.consulLongPoller = consulLongPoller;
        this.configMapClient = configMapClient;
        this.scheduler = scheduler;
        this.namespace = namespace;
        this.featureEnabled = featureEnabled;
    }

    /**
     * Starts the watcher and schedules periodic ownership checks.
     * <p>
     * This method is idempotent - calling it multiple times has no effect.
     *
     * @param compositeId the composite ID to watch
     */
    public void start(String compositeId) {
        if (started) {
            return;
        }
        if (compositeId == null || compositeId.isBlank()) {
            log.warn("Cannot start CompositeStructureWatcher: compositeId is null or blank");
            return;
        }

        log.info("Starting CompositeStructureWatcher for compositeId={}", compositeId);
        started = true;

        // Run immediately on start, then every CHECK_INTERVAL
        ensureWatchState(compositeId);

        scheduler.newJob(JOB_IDENTITY)
                .setInterval(CHECK_INTERVAL)
                .setTask(execution -> ensureWatchState(compositeId))
                .schedule();
    }

    private void ensureWatchState(String compositeId) {
        log.debug("VLLA ensureWatchState");
        if (!featureEnabled) {
            log.debug("Composite structure sync is disabled by configuration.");
            stopLongPoll();
            return;
        }

        try {
            boolean shouldManage = configMapClient.shouldBeManagedByCoreOperator(CONFIG_MAP_NAME, namespace);
            if (shouldManage) {
                startLongPoll(compositeId);
            } else {
                log.info("Composite structure watching is disabled because '{}' is not managed by core-operator.", CONFIG_MAP_NAME);
                stopLongPoll();
            }
        } catch (RuntimeException ex) {
            log.warn("Failed to verify management state for '{}'. Retrying on next schedule.", CONFIG_MAP_NAME, ex);
        }
    }

    private void startLongPoll(String compositeId) {
        if (isLongPollRunning()) {
            return;
        }
        String compositeStructureKey = COMPOSITE_STRUCTURE_KEY_TEMPLATE.formatted(compositeId);
        log.info("Starting Consul long-poll for key '{}'", compositeStructureKey);
        longPollSession = consulLongPoller.startWatch(compositeStructureKey, CompositeStructureUpdateEvent::new);
    }

    private void stopLongPoll() {
        if (longPollSession != null) {
            longPollSession.cancel();
            longPollSession = null;
            log.info("Consul long-poll stopped.");
        }
    }

    private boolean isLongPollRunning() {
        return longPollSession != null && !longPollSession.isCancelled();
    }
}
