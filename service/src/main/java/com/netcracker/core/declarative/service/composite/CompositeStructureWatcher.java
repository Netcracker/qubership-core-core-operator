package com.netcracker.core.declarative.service.composite;

import com.netcracker.cloud.quarkus.consul.client.model.GetValue;
import com.netcracker.core.declarative.service.composite.consul.longpoll2.CompositeStructureConsulUpdateEvent;
import com.netcracker.core.declarative.service.composite.consul.longpoll2.CompositeStructureRefConsulUpdateEvent;
import com.netcracker.core.declarative.service.composite.consul.longpoll2.ConsulLongPoller;
import com.netcracker.core.declarative.service.composite.consul.longpoll2.WatchHandle;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Objects;

/**
 * Watches Consul for composite structure changes using long-polling.
 * <p>
 * Monitors two Consul paths:
 * <ol>
 *   <li>{@code config/{namespace}/application/composite/structureRef} - reference to the current composite structure prefix</li>
 *   <li>The prefix itself - actual composite structure data</li>
 * </ol>
 * When the structure reference changes, automatically switches to watching the new prefix.
 * Structure updates are handled by {@link com.netcracker.core.declarative.service.composite.consul.longpoll2.CompositeStructureListener}.
 */
@ApplicationScoped
@Slf4j
public class CompositeStructureWatcher {

    private static final String COMPOSITE_STRUCTURE_REF_TEMPLATE = "config/%s/application/composite/structureRef";

    private final ConsulLongPoller consulLongPoller;
    private final String compositeStructureRefKey;
    private final Object lock = new Object();

    private volatile WatchHandle structureRefWatch;
    private volatile WatchHandle structureWatch;
    private volatile String currentCompositeStructureConsulPrefix;
    private volatile boolean stopped = true;

    @Inject
    public CompositeStructureWatcher(
            @ConfigProperty(name = "cloud.microservice.namespace") String namespace,
            ConsulLongPoller consulLongPoller) {
        this.consulLongPoller = consulLongPoller;
        this.compositeStructureRefKey = COMPOSITE_STRUCTURE_REF_TEMPLATE.formatted(namespace);
    }

    /**
     * Starts watching for composite structure changes.
     * <p>
     * First subscribes to the structureRef key. When its value is received,
     * subscribes to the actual structure prefix via CDI event handling.
     */
    public void start() {
        synchronized (lock) {
            if (!stopped) {
                log.debug("CompositeWatcher already started, skipping");
                return;
            }
            stopped = false;
            log.info("CompositeWatcher start. Composite Structure ref key = '{}'", compositeStructureRefKey);

            structureRefWatch = consulLongPoller.startWatchConsulRoot(
                    compositeStructureRefKey,
                    CompositeStructureRefConsulUpdateEvent::new
            );
        }
    }

    /**
     * Stops all watches and cleans up resources.
     */
    public void stop() {
        synchronized (lock) {
            stopped = true;
            if (structureRefWatch != null) {
                structureRefWatch.cancel();
                structureRefWatch = null;
            }
            if (structureWatch != null) {
                structureWatch.cancel();
                structureWatch = null;
            }
            currentCompositeStructureConsulPrefix = null;
            log.info("CompositeWatcher stopped.");
        }
    }

    /**
     * Handles structureRef update events from Consul.
     * <p>
     * Extracts the structure prefix from the event and switches the structure watcher if needed.
     */
    void onCompositeStructureRefSnapshot(@Observes CompositeStructureRefConsulUpdateEvent event) {
        synchronized (lock) {
            if (stopped) {
                log.debug("Received structureRef event but watcher is stopped, ignoring");
                return;
            }
        }

        String compositeStructurePrefix = extractStructureRef(event);
        log.info("Current composite structure prefix = '{}'", compositeStructurePrefix);
        switchCompositeStructurePoller(compositeStructurePrefix);
    }

    private String extractStructureRef(CompositeStructureRefConsulUpdateEvent event) {
        if (event.getValues() == null || event.getValues().isEmpty()) {
            return null;
        }
        // The structureRef key contains a single value - the path to the structure
        return event.getValues().stream()
                .filter(gv -> compositeStructureRefKey.equals(gv.getKey()))
                .map(GetValue::getDecodedValue)
                .findFirst()
                .orElse(null);
    }

    private void switchCompositeStructurePoller(String newCompositeStructureConsulPrefix) {
        synchronized (lock) {
            if (stopped) {
                return;
            }
            if (Objects.equals(currentCompositeStructureConsulPrefix, newCompositeStructureConsulPrefix)) {
                log.debug("Composite Structure Consul prefix is unchanged: '{}'", newCompositeStructureConsulPrefix);
                return;
            }
            if (structureWatch != null) {
                structureWatch.cancel();
                structureWatch = null;
            }
            currentCompositeStructureConsulPrefix = newCompositeStructureConsulPrefix;

            if (newCompositeStructureConsulPrefix == null || newCompositeStructureConsulPrefix.isBlank()) {
                log.warn("structureRef empty — structure polling paused.");
                return;
            }

            log.info("Switching composite structure polling to prefix = '{}'", newCompositeStructureConsulPrefix);

            structureWatch = consulLongPoller.startWatchConsulRoot(
                    newCompositeStructureConsulPrefix,
                    CompositeStructureConsulUpdateEvent::new
            );
        }
    }
}
