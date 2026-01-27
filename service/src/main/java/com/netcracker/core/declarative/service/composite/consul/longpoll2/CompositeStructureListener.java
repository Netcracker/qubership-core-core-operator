package com.netcracker.core.declarative.service.composite.consul.longpoll2;

import com.netcracker.core.declarative.service.composite.CompositeStructureSnapshotHandler;
import com.netcracker.core.declarative.service.composite.consul.model.ConsulPrefixSnapshot;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/**
 * Listens for composite structure update events and delegates to the snapshot handler.
 * <p>
 * Converts the CDI event to a {@link ConsulPrefixSnapshot} and triggers
 * ConfigMap update via {@link CompositeStructureSnapshotHandler}.
 */
@ApplicationScoped
@Slf4j
public class CompositeStructureListener {

    private final CompositeStructureSnapshotHandler snapshotHandler;

    @Inject
    public CompositeStructureListener(CompositeStructureSnapshotHandler snapshotHandler) {
        this.snapshotHandler = snapshotHandler;
    }

    void onStructureUpdated(@Observes CompositeStructureConsulUpdateEvent event) {
        log.info("Received composite structure update event with {} entries, consulIndex={}",
                event.getValues().size(), event.getConsulIndex());

        ConsulPrefixSnapshot snapshot = ConsulPrefixSnapshot.fromGetValues(
                event.getValues(),
                event.getConsulIndex()
        );

        snapshotHandler.handle(snapshot);
    }
}
