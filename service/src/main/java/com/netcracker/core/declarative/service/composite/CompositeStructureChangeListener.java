package com.netcracker.core.declarative.service.composite;

import com.netcracker.core.declarative.resources.composite.Composite;
import com.netcracker.core.declarative.service.CompositeCRHolder;
import com.netcracker.core.declarative.service.composite.consul.CompositeStructureUpdateEvent;
import com.netcracker.core.declarative.service.composite.model.CompositeStructure;
import com.netcracker.core.declarative.service.composite.model.transformation.CompositeStructureTransformer;

import static com.netcracker.core.declarative.service.composite.TopologyConfigMap.NAME;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles {@link CompositeStructureUpdateEvent} by transforming Consul data into a
 * {@link CompositeStructure} and publishing it to the {@code topology} ConfigMap.
 */
@ApplicationScoped
@Slf4j
public class CompositeStructureChangeListener {

    private final CompositeStructureTransformer compositeStructureTransformer;
    private final CompositeCRHolder compositeCRHolder;
    private final TopologyConfigMapPublisher topologyConfigMapPublisher;

    @Inject
    public CompositeStructureChangeListener(CompositeStructureTransformer compositeStructureTransformer,
                                            CompositeCRHolder compositeCRHolder,
                                            TopologyConfigMapPublisher topologyConfigMapPublisher) {
        this.compositeStructureTransformer = compositeStructureTransformer;
        this.compositeCRHolder = compositeCRHolder;
        this.topologyConfigMapPublisher = topologyConfigMapPublisher;
    }

    void onStructureUpdated(@Observes CompositeStructureUpdateEvent event) {
        log.info("Received composite structure update from Consul with {} entries", event.getValues().size());
        try {
            CompositeStructure structure = compositeStructureTransformer.transform(event.getValues());

            Composite composite = compositeCRHolder.get();
            if (composite == null) {
                log.warn("Composite CR not available yet, skipping ConfigMap update. " +
                        "ConfigMap will be updated on next Consul change after CR reconciliation.");
                return;
            }

            topologyConfigMapPublisher.publish(structure, composite)
                    .exceptionally(ex -> {
                        log.error("Failed to publish ConfigMap '{}' after all retries", NAME, ex);
                        return null;
                    });
        } catch (Exception e) {
            log.error("Failed to process composite structure update from Consul", e);
        }
    }
}
