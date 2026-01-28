package com.netcracker.core.declarative.service.composite;

import com.netcracker.core.declarative.client.k8s.ConfigMapClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ConfigMapWriterTest {

    private static final String NAMESPACE = "test-namespace";
    private static final String CONFIG_MAP_NAME = "composite-structure";

    private ConfigMapClient configMapClient;
    private ConfigMapWriter writer;

    @BeforeEach
    void setUp() {
        configMapClient = mock(ConfigMapClient.class);
        writer = new ConfigMapWriter(configMapClient, NAMESPACE);
    }

    @Test
    void requestUpdateShouldCallConfigMapClient() {
        Map<String, String> payload = Map.of("key", "value");

        CompletionStage<Void> result = writer.requestUpdate(CONFIG_MAP_NAME, payload);

        assertThat(result).isNotNull();
        verify(configMapClient).createOrUpdate(eq(CONFIG_MAP_NAME), eq(NAMESPACE), eq(payload));
    }

    @Test
    void requestUpdateShouldThrowOnNullConfigMapName() {
        Map<String, String> payload = Map.of("key", "value");

        assertThatThrownBy(() -> writer.requestUpdate(null, payload))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("configMapName");
    }

    @Test
    void requestUpdateShouldThrowOnNullPayload() {
        assertThatThrownBy(() -> writer.requestUpdate(CONFIG_MAP_NAME, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("payload");
    }

    @Test
    void requestUpdateShouldCreateDefensiveCopyOfPayload() {
        Map<String, String> mutablePayload = new HashMap<>();
        mutablePayload.put("key", "original");

        writer.requestUpdate(CONFIG_MAP_NAME, mutablePayload);

        // Modify original after call
        mutablePayload.put("key", "modified");

        // Verify the original value was passed (defensive copy was made)
        verify(configMapClient).createOrUpdate(eq(CONFIG_MAP_NAME), eq(NAMESPACE), eq(Map.of("key", "original")));
    }
}
