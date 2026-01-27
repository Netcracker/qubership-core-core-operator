package com.netcracker.core.declarative.service.composite;

import com.netcracker.cloud.quarkus.consul.client.model.GetValue;
import com.netcracker.core.declarative.service.composite.consul.CompositeStructureUpdateEvent;
import com.netcracker.core.declarative.service.composite.consul.CompositeStructureRefUpdateEvent;
import com.netcracker.core.declarative.service.composite.consul.longpoll.ConsulLongPoller;
import com.netcracker.core.declarative.service.composite.consul.longpoll.LongPollSession;
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
 * Structure updates are handled by {@link CompositeStructureChangeListener}.
 */
@ApplicationScoped
@Slf4j
public class CompositeStructureRefChangeListener {

    private static final String COMPOSITE_STRUCTURE_REF_TEMPLATE = "config/%s/application/composite/structureRef";

    private final ConsulLongPoller consulLongPoller;
    private final String compositeStructureRefKey;

    private LongPollSession compositeStructureRefSession;
    private LongPollSession compositeStructureSession;
    private String currentCompositeStructurePrefix;

    @Inject
    public CompositeStructureRefChangeListener(
            @ConfigProperty(name = "cloud.microservice.namespace") String namespace,
            ConsulLongPoller consulLongPoller) {
        this.consulLongPoller = consulLongPoller;
        this.compositeStructureRefKey = COMPOSITE_STRUCTURE_REF_TEMPLATE.formatted(namespace);
    }

    public synchronized void start() {
        if (isRunning()) {
            log.debug("CompositeWatcher already started, skipping");
            return;
        }
        log.info("CompositeWatcher start. Composite Structure ref key = '{}'", compositeStructureRefKey);
        compositeStructureRefSession = consulLongPoller.startWatch(
                compositeStructureRefKey,
                CompositeStructureRefUpdateEvent::new
        );
    }

    public synchronized void stop() {
        cancelWatch(compositeStructureRefSession);
        cancelWatch(compositeStructureSession);
        compositeStructureRefSession = null;
        compositeStructureSession = null;
        currentCompositeStructurePrefix = null;
        log.info("CompositeWatcher stopped.");
    }

    synchronized void onCompositeStructureRefUpdated(@Observes CompositeStructureRefUpdateEvent event) {
        if (!isRunning()) {
            log.debug("Received structureRef event but watcher is stopped, ignoring");
            return;
        }

        String newPrefix = extractCompositeStructurePrefix(event);
        log.info("Current composite structure prefix = '{}'", newPrefix);
        switchCompositeStructureWatch(newPrefix);
    }

    private synchronized void switchCompositeStructureWatch(String newPrefix) {
        if (!isRunning()) {
            return;
        }
        if (Objects.equals(currentCompositeStructurePrefix, newPrefix)) {
            log.debug("Composite Structure Consul prefix is unchanged: '{}'", newPrefix);
            return;
        }

        cancelWatch(compositeStructureSession);
        compositeStructureSession = null;
        currentCompositeStructurePrefix = newPrefix;

        if (newPrefix == null || newPrefix.isBlank()) {
            log.warn("structureRef empty — structure polling paused.");
            return;
        }

        log.info("Switching composite structure polling to prefix = '{}'", newPrefix);
        compositeStructureSession = consulLongPoller.startWatch(
                newPrefix,
                CompositeStructureUpdateEvent::new
        );
    }

    private boolean isRunning() {
        return compositeStructureRefSession != null && !compositeStructureRefSession.isCancelled();
    }

    private void cancelWatch(LongPollSession watch) {
        if (watch != null) {
            watch.cancel();
        }
    }

    private String extractCompositeStructurePrefix(CompositeStructureRefUpdateEvent event) {
        if (event.getValues() == null || event.getValues().isEmpty()) {
            return null;
        }
        return event.getValues().stream()
                .filter(gv -> compositeStructureRefKey.equals(gv.getKey()))
                .map(GetValue::getDecodedValue)
                .findFirst()
                .orElse(null);
    }
}
