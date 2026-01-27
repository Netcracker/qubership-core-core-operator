package com.netcracker.core.declarative.service.composite.model.transformation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netcracker.cloud.quarkus.consul.client.model.GetValue;
import com.netcracker.core.declarative.service.composite.model.CompositeStructureConfigMapPayload;
import com.netcracker.core.declarative.service.composite.model.ConsulSnapshotSerializationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompositeStructureTransformerTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private CompositeStructureTransformer compositeStructureTransformer;

    @BeforeEach
    void setUp() {
        compositeStructureTransformer = new CompositeStructureTransformer("test-provider");
    }

    @Test
    void standaloneTest() {
        Map<String, String> keyValues = new LinkedHashMap<>() {{
            put("composite/bs-origin/structure/bs-origin/compositeRole", "baseline");
        }};

        String json = serialize(keyValues);

        assertEquals("{\"cloudProvider\":\"test-provider\",\"composite\":{\"baseline\":{\"origin\":\"bs-origin\"}}}",
                json);
    }

    @Test
    void blueGreenTest() {
        Map<String, String> keyValues = new LinkedHashMap<>() {{
            put("composite/bs-origin/structure/bs-controller/bluegreenRole", "controller");
            put("composite/bs-origin/structure/bs-controller/compositeRole", "baseline");
            put("composite/bs-origin/structure/bs-origin/bluegreenRole", "origin");
            put("composite/bs-origin/structure/bs-origin/compositeRole", "baseline");
            put("composite/bs-origin/structure/bs-origin/controllerNamespace", "bs-controller");
            put("composite/bs-origin/structure/bs-peer/bluegreenRole", "peer");
            put("composite/bs-origin/structure/bs-peer/compositeRole", "baseline");
            put("composite/bs-origin/structure/bs-peer/controllerNamespace", "bs-controller");
        }};

        String json = serialize(keyValues);

        assertEquals("{\"cloudProvider\":\"test-provider\",\"composite\":{\"baseline\":{\"controller\":\"bs-controller\",\"origin\":\"bs-origin\",\"peer\":\"bs-peer\"}}}",
                json);
    }

    @Test
    void compositeTest() {
        Map<String, String> keyValues = new LinkedHashMap<>() {{
            put("composite/bs-origin/structure/bs-origin/compositeRole", "baseline");
            put("composite/bs-origin/structure/st-1-origin/compositeRole", "satellite");
            put("composite/bs-origin/structure/st-2-origin/compositeRole", "satellite");
        }};

        String json = serialize(keyValues);

        assertEquals("{\"cloudProvider\":\"test-provider\",\"composite\":{\"baseline\":{\"origin\":\"bs-origin\"},\"satellites\":[{\"origin\":\"st-1-origin\"},{\"origin\":\"st-2-origin\"}]}}",
                json);
    }

    @Test
    void compositeBlueGreenAllTest() {
        Map<String, String> keyValues = new LinkedHashMap<>() {{
            put("composite/bs-origin/structure/bs-controller/bluegreenRole", "controller");
            put("composite/bs-origin/structure/bs-controller/compositeRole", "baseline");
            put("composite/bs-origin/structure/bs-origin/bluegreenRole", "origin");
            put("composite/bs-origin/structure/bs-origin/compositeRole", "baseline");
            put("composite/bs-origin/structure/bs-origin/controllerNamespace", "bs-controller");
            put("composite/bs-origin/structure/bs-peer/bluegreenRole", "peer");
            put("composite/bs-origin/structure/bs-peer/compositeRole", "baseline");
            put("composite/bs-origin/structure/bs-peer/controllerNamespace", "bs-controller");
            put("composite/bs-origin/structure/st-1-controller/bluegreenRole", "controller");
            put("composite/bs-origin/structure/st-1-controller/compositeRole", "satellite");
            put("composite/bs-origin/structure/st-1-origin/bluegreenRole", "origin");
            put("composite/bs-origin/structure/st-1-origin/compositeRole", "satellite");
            put("composite/bs-origin/structure/st-1-origin/controllerNamespace", "st-1-controller");
            put("composite/bs-origin/structure/st-1-peer/bluegreenRole", "peer");
            put("composite/bs-origin/structure/st-1-peer/compositeRole", "satellite");
            put("composite/bs-origin/structure/st-1-peer/controllerNamespace", "st-1-controller");
            put("composite/bs-origin/structure/st-2-controller/bluegreenRole", "controller");
            put("composite/bs-origin/structure/st-2-controller/compositeRole", "satellite");
            put("composite/bs-origin/structure/st-2-origin/bluegreenRole", "origin");
            put("composite/bs-origin/structure/st-2-origin/compositeRole", "satellite");
            put("composite/bs-origin/structure/st-2-origin/controllerNamespace", "st-2-controller");
            put("composite/bs-origin/structure/st-2-peer/bluegreenRole", "peer");
            put("composite/bs-origin/structure/st-2-peer/compositeRole", "satellite");
            put("composite/bs-origin/structure/st-2-peer/controllerNamespace", "st-2-controller");
        }};

        String json = serialize(keyValues);

        assertEquals("{\"cloudProvider\":\"test-provider\",\"composite\":{\"baseline\":{\"controller\":\"bs-controller\",\"origin\":\"bs-origin\",\"peer\":\"bs-peer\"},\"satellites\":[{\"controller\":\"st-1-controller\",\"origin\":\"st-1-origin\",\"peer\":\"st-1-peer\"},{\"controller\":\"st-2-controller\",\"origin\":\"st-2-origin\",\"peer\":\"st-2-peer\"}]}}",
                json);
    }

    @Test
    void compositeBlueGreenSomeTest() {
        Map<String, String> keyValues = new LinkedHashMap<>() {{
            put("composite/bs-origin/structure/bs-controller/bluegreenRole", "controller");
            put("composite/bs-origin/structure/bs-controller/compositeRole", "baseline");
            put("composite/bs-origin/structure/bs-origin/bluegreenRole", "origin");
            put("composite/bs-origin/structure/bs-origin/compositeRole", "baseline");
            put("composite/bs-origin/structure/bs-origin/controllerNamespace", "bs-controller");
            put("composite/bs-origin/structure/bs-peer/bluegreenRole", "peer");
            put("composite/bs-origin/structure/bs-peer/compositeRole", "baseline");
            put("composite/bs-origin/structure/bs-peer/controllerNamespace", "bs-controller");
            put("composite/bs-origin/structure/st-1-controller/bluegreenRole", "controller");
            put("composite/bs-origin/structure/st-1-controller/compositeRole", "satellite");
            put("composite/bs-origin/structure/st-1-origin/bluegreenRole", "origin");
            put("composite/bs-origin/structure/st-1-origin/compositeRole", "satellite");
            put("composite/bs-origin/structure/st-1-origin/controllerNamespace", "st-1-controller");
            put("composite/bs-origin/structure/st-1-peer/bluegreenRole", "peer");
            put("composite/bs-origin/structure/st-1-peer/compositeRole", "satellite");
            put("composite/bs-origin/structure/st-1-peer/controllerNamespace", "st-1-controller");
            put("composite/bs-origin/structure/st-2-origin/compositeRole", "satellite");
        }};

        String json = serialize(keyValues);

        assertEquals("{\"cloudProvider\":\"test-provider\",\"composite\":{\"baseline\":{\"controller\":\"bs-controller\",\"origin\":\"bs-origin\",\"peer\":\"bs-peer\"},\"satellites\":[{\"controller\":\"st-1-controller\",\"origin\":\"st-1-origin\",\"peer\":\"st-1-peer\"},{\"origin\":\"st-2-origin\"}]}}",
                json);
    }

    @Test
    void shouldIgnoreKeysNotMatchingPattern() {
        Map<String, String> keyValues = new LinkedHashMap<>() {{
            put("composite/bs-origin/structure/bs-origin/compositeRole", "baseline");
            put("some/other/key", "value");
            put("composite/bs-origin/invalid-path/ns/attr", "value");
        }};

        String json = serialize(keyValues);

        assertEquals("{\"cloudProvider\":\"test-provider\",\"composite\":{\"baseline\":{\"origin\":\"bs-origin\"}}}",
                json);
    }

    @Test
    void shouldThrowOnInvalidBlueGreenRole() {
        Map<String, String> keyValues = new LinkedHashMap<>() {{
            put("composite/bs-origin/structure/bs-origin/compositeRole", "baseline");
            put("composite/bs-origin/structure/bs-origin/bluegreenRole", "INVALID_BG_ROLE");
        }};

        List<GetValue> values = keyValues.entrySet().stream()
                .map(entry -> {
                    GetValue gv = mock(GetValue.class);
                    when(gv.getKey()).thenReturn(entry.getKey());
                    when(gv.getDecodedValue()).thenReturn(entry.getValue());
                    return gv;
                })
                .toList();

        assertThatThrownBy(() -> compositeStructureTransformer.transform(values))
                .isInstanceOf(ConsulSnapshotSerializationException.class)
                .hasMessageContaining("blue-green role");
    }

    @Test
    void shouldThrowOnInvalidCompositeRole() {
        Map<String, String> keyValues = new LinkedHashMap<>() {{
            put("composite/bs-origin/structure/bs-origin/compositeRole", "INVALID_COMPOSITE_ROLE");
        }};

        List<GetValue> values = keyValues.entrySet().stream()
                .map(entry -> {
                    GetValue gv = mock(GetValue.class);
                    when(gv.getKey()).thenReturn(entry.getKey());
                    when(gv.getDecodedValue()).thenReturn(entry.getValue());
                    return gv;
                })
                .toList();

        assertThatThrownBy(() -> compositeStructureTransformer.transform(values))
                .isInstanceOf(ConsulSnapshotSerializationException.class)
                .hasMessageContaining("composite role");
    }

    private String serialize(Map<String, String> keyValues) {
        List<GetValue> values = keyValues.entrySet().stream()
                .map(entry -> {
                    GetValue gv = mock(GetValue.class);
                    when(gv.getKey()).thenReturn(entry.getKey());
                    when(gv.getDecodedValue()).thenReturn(entry.getValue());
                    return gv;
                })
                .toList();
        CompositeStructureConfigMapPayload payload = compositeStructureTransformer.transform(values);
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
