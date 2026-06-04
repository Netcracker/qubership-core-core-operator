package com.netcracker.core.declarative.service.composite;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netcracker.core.declarative.resources.composite.Composite;
import com.netcracker.core.declarative.service.CloudProviderResolver;
import com.netcracker.core.declarative.service.composite.model.CompositeStructure;
import com.netcracker.core.declarative.service.composite.model.CompositeStructureConfigMapPayload;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.CompletionStage;

import static com.netcracker.core.declarative.service.composite.TopologyConfigMap.DATA_KEY;
import static com.netcracker.core.declarative.service.composite.TopologyConfigMap.NAME;

/**
 * Writes the {@value TopologyConfigMap#NAME} ConfigMap from a {@link CompositeStructure}.
 * <p>
 * Stamps the resolved cloud provider onto the structure, serializes the payload, and persists it
 * via {@link ConfigMapWriter}.
 */
@ApplicationScoped
@Slf4j
public class TopologyConfigMapPublisher {

    private final ConfigMapWriter configMapWriter;
    private final ObjectMapper objectMapper;
    private final CloudProviderResolver cloudProviderResolver;

    @Inject
    public TopologyConfigMapPublisher(ConfigMapWriter configMapWriter,
                                      ObjectMapper objectMapper,
                                      CloudProviderResolver cloudProviderResolver) {
        this.configMapWriter = configMapWriter;
        this.objectMapper = objectMapper;
        this.cloudProviderResolver = cloudProviderResolver;
    }

    /**
     * Stamps the cloud provider onto {@code structure}, serializes it, and writes it to the
     * {@value TopologyConfigMap#NAME} ConfigMap owned by {@code owner}.
     *
     * @param structure the composite structure (may be {@code null} when there is no topology)
     * @param owner     the Composite CR set as owner of the ConfigMap
     * @return a future that completes when the ConfigMap is written, or fails after all retries
     * @throws RuntimeException if the payload cannot be serialized
     */
    public CompletionStage<Void> publish(CompositeStructure structure, Composite owner) {
        CompositeStructureConfigMapPayload payload =
                new CompositeStructureConfigMapPayload(cloudProviderResolver.get().getValue(), structure);
        log.info("Publishing '{}' ConfigMap: {}", NAME, payload);

        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize topology payload for ConfigMap '" + NAME + "'", e);
        }

        Map<String, String> data = Map.of(DATA_KEY, json);
        return configMapWriter.requestUpdate(NAME, data, owner)
                .thenRun(() -> log.info("Successfully published ConfigMap '{}'", NAME));
    }
}
