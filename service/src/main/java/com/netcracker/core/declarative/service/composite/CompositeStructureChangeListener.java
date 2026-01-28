package com.netcracker.core.declarative.service.composite;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netcracker.core.declarative.service.composite.consul.CompositeStructureUpdateEvent;
import com.netcracker.core.declarative.service.composite.model.CompositeStructureConfigMapPayload;
import com.netcracker.core.declarative.service.composite.model.CompositeStructureParseException;
import com.netcracker.core.declarative.service.composite.model.transformation.CompositeStructureTransformer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

import static com.netcracker.core.declarative.service.composite.CompositeStructureWatchCoordinator.CONFIG_MAP_NAME;

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

    @Inject
    public CompositeStructureChangeListener(ObjectMapper objectMapper,
                                            ConfigMapWriter configMapWriter,
                                            CompositeStructureTransformer compositeStructureTransformer) {
        this.objectMapper = objectMapper;
        this.configMapWriter = configMapWriter;
        this.compositeStructureTransformer = compositeStructureTransformer;
    }

    void onStructureUpdated(@Observes CompositeStructureUpdateEvent event) {
        log.info("Caught CompositeStructureConsulUpdateEvent -> Store Composite Structure to config map {}", CONFIG_MAP_NAME);
        try {
            CompositeStructureConfigMapPayload payload = compositeStructureTransformer.transform(event.getValues());
            String json = objectMapper.writeValueAsString(payload);
            Map<String, String> compositeStructureContent = Map.of(CONFIG_MAP_DATA_KEY, json);
            configMapWriter.requestUpdate(CONFIG_MAP_NAME, compositeStructureContent);
        } catch (JsonProcessingException e) {
            throw new CompositeStructureParseException("Failed to serialize composite structure to JSON", e);
        }
    }
}
