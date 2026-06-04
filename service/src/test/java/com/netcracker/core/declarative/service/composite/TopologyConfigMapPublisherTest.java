package com.netcracker.core.declarative.service.composite;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netcracker.core.declarative.resources.composite.Composite;
import com.netcracker.core.declarative.service.CloudProviderDetector;
import com.netcracker.core.declarative.service.CloudProviderResolver;
import com.netcracker.core.declarative.service.composite.model.CloudProvider;
import com.netcracker.core.declarative.service.composite.model.CompositeStructure;
import com.netcracker.core.declarative.service.composite.model.CompositeStructure.NamespaceRoles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.netcracker.core.declarative.service.composite.TopologyConfigMap.DATA_KEY;
import static com.netcracker.core.declarative.service.composite.TopologyConfigMap.NAME;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class TopologyConfigMapPublisherTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ConfigMapWriter configMapWriter;
    private CloudProviderDetector cloudProviderDetector;

    @BeforeEach
    void setUp() {
        configMapWriter = mock(ConfigMapWriter.class);
        when(configMapWriter.requestUpdate(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        cloudProviderDetector = mock(CloudProviderDetector.class);
        when(cloudProviderDetector.getCloudProvider()).thenReturn(CloudProvider.ON_PREM);
    }

    private TopologyConfigMapPublisher newPublisher(Optional<String> cloudProvider) {
        CloudProviderResolver resolver = new CloudProviderResolver(cloudProvider, cloudProviderDetector);
        return new TopologyConfigMapPublisher(configMapWriter, objectMapper, resolver);
    }

    @Test
    void stampsProviderAndSerializesStructure() throws JsonProcessingException {
        TopologyConfigMapPublisher publisher = newPublisher(Optional.of("EKS"));
        CompositeStructure structure = new CompositeStructure(
                new NamespaceRoles("ctrl", "origin", "peer"),
                List.of(new NamespaceRoles(null, "sat-origin", null)));
        Composite owner = mock(Composite.class);

        publisher.publish(structure, owner);

        JsonNode root = capturePayload(owner);
        assertEquals("EKS", root.get("cloudProvider").asText());
        assertEquals("origin", root.get("composite").get("baseline").get("origin").asText());
        assertEquals("sat-origin", root.get("composite").get("satellites").get(0).get("origin").asText());
    }

    @Test
    void nullStructureProducesProviderOnlyPayload() throws JsonProcessingException {
        TopologyConfigMapPublisher publisher = newPublisher(Optional.of("OnPrem"));
        Composite owner = mock(Composite.class);

        publisher.publish(null, owner);

        JsonNode root = capturePayload(owner);
        assertEquals("OnPrem", root.get("cloudProvider").asText());
        assertFalse(root.has("composite"));
    }

    @Test
    void cloudProviderFallsBackToDetector() throws JsonProcessingException {
        TopologyConfigMapPublisher publisher = newPublisher(Optional.empty());
        Composite owner = mock(Composite.class);

        publisher.publish(new CompositeStructure(new NamespaceRoles(null, "o", null), null), owner);

        assertEquals(CloudProvider.ON_PREM.getValue(), capturePayload(owner).get("cloudProvider").asText());
    }

    @Test
    void passesCompositeAsOwner() {
        TopologyConfigMapPublisher publisher = newPublisher(Optional.of("EKS"));
        Composite owner = mock(Composite.class);

        publisher.publish(new CompositeStructure(new NamespaceRoles(null, "o", null), null), owner);

        verify(configMapWriter).requestUpdate(eq(NAME), any(), eq(owner));
    }

    @SuppressWarnings("unchecked")
    private JsonNode capturePayload(Composite owner) throws JsonProcessingException {
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(configMapWriter).requestUpdate(eq(NAME), captor.capture(), eq(owner));
        String json = captor.getValue().get(DATA_KEY);
        assertNotNull(json);
        return objectMapper.readTree(json);
    }
}
