package com.netcracker.core.declarative.service.composite;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netcracker.core.declarative.resources.composite.Composite;
import com.netcracker.core.declarative.service.CompositeCRHolder;
import com.netcracker.core.declarative.service.composite.consul.CompositeStructureUpdateEvent;
import com.netcracker.core.declarative.service.composite.model.CompositeStructureConfigMapPayload;
import com.netcracker.core.declarative.service.composite.model.transformation.CompositeStructureTransformer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

import static com.netcracker.core.declarative.service.composite.CompositeStructureWatcher.CONFIG_MAP_NAME;

/**
 * Handles {@link CompositeStructureUpdateEvent} by transforming Consul data
 * and persisting it to the {@code composite-structure} ConfigMap.
 */
@ApplicationScoped
@Slf4j
public class CompositeStructureChangeListener {
    private static final String CONFIG_MAP_DATA_KEY = "data";

    private final ObjectMapper objectMapper;
    private final ConfigMapWriter configMapWriter;
    private final CompositeStructureTransformer compositeStructureTransformer;
    private final CompositeCRHolder compositeCRHolder;

    @Inject
    public CompositeStructureChangeListener(ObjectMapper objectMapper,
                                            ConfigMapWriter configMapWriter,
                                            CompositeStructureTransformer compositeStructureTransformer,
                                            CompositeCRHolder compositeCRHolder) {
        this.objectMapper = objectMapper;
        this.configMapWriter = configMapWriter;
        this.compositeStructureTransformer = compositeStructureTransformer;
        this.compositeCRHolder = compositeCRHolder;
    }

    void onStructureUpdated(@Observes CompositeStructureUpdateEvent event) {
        log.info("Received composite structure update from Consul with {} entries", event.getValues().size());
        try {
            CompositeStructureConfigMapPayload payload = compositeStructureTransformer.transform(event.getValues());
            log.debug("Transformed composite structure: {}", payload);

            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
            Map<String, String> compositeStructureContent = Map.of(CONFIG_MAP_DATA_KEY, json);

            Composite composite = compositeCRHolder.get();
            if (composite == null) {
                log.warn("Composite CR not available yet, skipping ConfigMap update. " +
                        "ConfigMap will be updated on next Consul change after CR reconciliation.");
                return;
            }

            configMapWriter.requestUpdate(CONFIG_MAP_NAME, compositeStructureContent, composite)
                    .thenRun(() -> log.info("Successfully updated ConfigMap '{}'", CONFIG_MAP_NAME))
                    .exceptionally(ex -> {
                        log.error("Failed to update ConfigMap '{}' after all retries", CONFIG_MAP_NAME, ex);
                        return null;
                    });
        } catch (Exception e) {
            log.error("Failed to process composite structure update from Consul", e);
        }
    }
}
