package com.netcracker.core.declarative.service.composite;

import com.netcracker.core.declarative.client.k8s.ConfigMapClient;
import com.netcracker.core.declarative.service.composite.consul.CompositeStructureUpdateEvent;
import com.netcracker.core.declarative.service.composite.consul.longpoll.ConsulLongPoller;
import com.netcracker.core.declarative.service.composite.consul.longpoll.LongPollSession;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduler;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;
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
@ApplicationScoped
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
    private String pendingCompositeId;
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
    public synchronized void start(String compositeId) {
        if (started) {
            log.debug("CompositeStructureWatcher already started, ignoring");
            return;
        }
        if (compositeId == null || compositeId.isBlank()) {
            log.warn("Cannot start CompositeStructureWatcher: compositeId is null or blank");
            return;
        }

        log.info("Starting CompositeStructureWatcher for compositeId={}", compositeId);
        started = true;

        ensureWatchState(compositeId);

        if (scheduler.isStarted()) {
            scheduleJob(compositeId);
        } else {
            log.debug("Scheduler not ready, deferring job scheduling to startup event");
            pendingCompositeId = compositeId;
        }
    }

    synchronized void onStartup(@Observes StartupEvent event) {
        log.debug("VLLA onStartup");
        if (pendingCompositeId != null) {
            log.debug("Scheduler ready, scheduling deferred job for compositeId={}", pendingCompositeId);
            scheduleJob(pendingCompositeId);
            pendingCompositeId = null;
        }
    }

    private void scheduleJob(String compositeId) {
        scheduler.newJob(JOB_IDENTITY)
                .setInterval(CHECK_INTERVAL)
                .setTask(execution -> ensureWatchState(compositeId))
                .schedule();
    }

    private synchronized void ensureWatchState(String compositeId) {
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
            log.info("Stopping Consul long-poll for '{}' (ConfigMap not managed by core-operator)", CONFIG_MAP_NAME);
            longPollSession.cancel();
            longPollSession = null;
        }
    }

    private boolean isLongPollRunning() {
        return longPollSession != null && !longPollSession.isCancelled();
    }
}
