package com.netcracker.core.declarative.service.composite;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netcracker.cloud.quarkus.consul.client.model.GetValue;
import com.netcracker.core.declarative.resources.composite.Composite;
import com.netcracker.core.declarative.service.CompositeCRHolder;
import com.netcracker.core.declarative.service.composite.consul.CompositeStructureUpdateEvent;
import com.netcracker.core.declarative.service.composite.model.transformation.CompositeStructureTransformer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static com.netcracker.core.declarative.service.composite.CompositeStructureWatcher.CONFIG_MAP_NAME;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CompositeStructureChangeListenerTest {

    private static final String CLOUD_PROVIDER = "test-provider";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ConfigMapWriter configMapWriter;
    private CompositeCRHolder compositeCRHolder;
    private CompositeStructureChangeListener listener;

    @BeforeEach
    void setUp() {
        configMapWriter = mock(ConfigMapWriter.class);
        when(configMapWriter.requestUpdate(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        compositeCRHolder = mock(CompositeCRHolder.class);
        // By default, return a mock Composite CR (non-null)
        when(compositeCRHolder.get()).thenReturn(mock(Composite.class));

        CompositeStructureTransformer compositeStructureTransformer = new CompositeStructureTransformer(CLOUD_PROVIDER);
        listener = new CompositeStructureChangeListener(
                objectMapper,
                configMapWriter,
                compositeStructureTransformer,
                compositeCRHolder
        );
    }

    @Test
    void handleShouldSerializeSnapshotAndUpdateConfigMap() throws JsonProcessingException {
        CompositeStructureUpdateEvent event = createEvent(Map.of(
                "composite/sample/structure/ns-a/compositeRole", "baseline",
                "composite/sample/structure/ns-b/compositeRole", "satellite"
        ));

        listener.onStructureUpdated(event);

        Map<String, String> capturedData = captureConfigMapData();
        String json = capturedData.get("data");
        assertNotNull(json);

        JsonNode root = objectMapper.readTree(json);
        assertEquals(CLOUD_PROVIDER, root.get("cloudProvider").asText());

        JsonNode composite = root.get("composite");
        assertNotNull(composite);
        assertEquals("ns-a", composite.get("baseline").get("origin").asText());
        assertEquals(1, composite.get("satellites").size());
        assertEquals("ns-b", composite.get("satellites").get(0).get("origin").asText());
    }

    @Test
    void handleShouldNotThrowOnTransformationError() {
        CompositeStructureUpdateEvent event = createEvent(Map.of(
                "composite/sample/structure/ns-a/compositeRole", "INVALID_ROLE"
        ));

        // Should not throw exception - errors are caught and logged
        assertDoesNotThrow(() -> listener.onStructureUpdated(event));

        // ConfigMapWriter should not be called when transformation fails
        verifyNoInteractions(configMapWriter);
    }

    @Test
    void handleShouldProcessEmptyValuesList() throws JsonProcessingException {
        CompositeStructureUpdateEvent event = new CompositeStructureUpdateEvent(Collections.emptyList(), 0);

        listener.onStructureUpdated(event);

        Map<String, String> capturedData = captureConfigMapData();
        String json = capturedData.get("data");
        assertNotNull(json);

        JsonNode root = objectMapper.readTree(json);
        assertEquals(CLOUD_PROVIDER, root.get("cloudProvider").asText());
        // composite should be empty/null when no values
        assertFalse(root.has("composite") && root.get("composite").has("baseline"));
    }

    @Test
    void handleShouldSkipUpdateWhenCompositeIsNull() {
        // Setup: compositeCRHolder returns null (CR not available yet)
        when(compositeCRHolder.get()).thenReturn(null);

        CompositeStructureUpdateEvent event = createEvent(Map.of(
                "composite/sample/structure/ns-a/compositeRole", "baseline"
        ));

        // Should not throw exception
        assertDoesNotThrow(() -> listener.onStructureUpdated(event));

        // ConfigMapWriter should not be called when CR is null
        verifyNoInteractions(configMapWriter);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> captureConfigMapData() {
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(configMapWriter).requestUpdate(eq(CONFIG_MAP_NAME), captor.capture(), any());
        return captor.getValue();
    }

    private static CompositeStructureUpdateEvent createEvent(Map<String, String> keyValues) {
        List<GetValue> values = keyValues.entrySet().stream()
                .map(entry -> {
                    GetValue gv = mock(GetValue.class);
                    when(gv.getKey()).thenReturn(entry.getKey());
                    when(gv.getDecodedValue()).thenReturn(entry.getValue());
                    return gv;
                })
                .toList();
        return new CompositeStructureUpdateEvent(values, 0);
    }

}