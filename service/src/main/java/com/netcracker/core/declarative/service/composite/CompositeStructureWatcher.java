package com.netcracker.core.declarative.service.composite;

import com.netcracker.core.declarative.client.k8s.ConfigMapClient;
import com.netcracker.core.declarative.service.composite.consul.CompositeStructureUpdateEvent;
import com.netcracker.core.declarative.service.composite.consul.longpoll.ConsulLongPoller;
import com.netcracker.core.declarative.service.composite.consul.longpoll.LongPollSession;
import io.vertx.mutiny.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.concurrent.TimeUnit;

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
@ApplicationScoped
@Slf4j
public class CompositeStructureWatcher {
    public static final String CONFIG_MAP_NAME = "composite-structure";
    private static final long CHECK_INTERVAL_MS = TimeUnit.MINUTES.toMillis(5);
    private static final String COMPOSITE_STRUCTURE_KEY_TEMPLATE = "composite/%s/structure";

    private final ConsulLongPoller consulLongPoller;
    private final ConfigMapClient configMapClient;
    private final Vertx vertx;
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
            Vertx vertx) {
        this.consulLongPoller = consulLongPoller;
        this.configMapClient = configMapClient;
        this.vertx = vertx;
        this.namespace = namespace;
        this.featureEnabled = featureEnabled;
    }

    /**
     * Starts the watcher and schedules periodic ownership checks.
     *
     * @param compositeId the composite ID to watch
     */
    public synchronized void start(String compositeId) {
        if (started) {
            log.debug("CompositeStructureWatcher already started, ignoring");
            return;
        }
        if (compositeId == null || compositeId.isBlank()) {
            log.warn("Cannot start CompositeStructureWatcher: compositeId is null or blank");
            return;
        }

        if (!featureEnabled) {
            log.info("Composite structure sync is disabled by configuration");
            return;
        }

        log.info("Starting CompositeStructureWatcher for compositeId={}", compositeId);
        started = true;

        // Run immediately, then schedule periodic checks
        ensureWatchState(compositeId);
        vertx.setPeriodic(CHECK_INTERVAL_MS, id -> ensureWatchState(compositeId));
    }

    private synchronized void ensureWatchState(String compositeId) {
        try {
            boolean shouldManage = configMapClient.shouldBeManagedByCoreOperator(CONFIG_MAP_NAME, namespace);
            if (shouldManage) {
                startLongPoll(compositeId);
            } else {
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
        if (isLongPollRunning()) {
            log.info("Stopping Consul long-poll for '{}'", CONFIG_MAP_NAME);
            longPollSession.cancel();
            longPollSession = null;
        }
    }

    private boolean isLongPollRunning() {
        return longPollSession != null && !longPollSession.isCancelled();
    }
}
