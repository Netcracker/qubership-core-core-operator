package com.netcracker.core.declarative.service.composite;

import com.netcracker.core.declarative.client.k8s.ConfigMapClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

class ConfigMapWriterTest {

    private static final String NAMESPACE = "test-namespace";
    private static final String CONFIG_MAP_NAME = "composite-structure";

    @Test
    void requestUpdateShouldReturnCompletionStage() {
        ConfigMapClient mockClient = Mockito.mock(ConfigMapClient.class);
        ConfigMapWriter testWriter = new ConfigMapWriter(mockClient, NAMESPACE);

        Map<String, String> payload = Map.of("key", "value");

        CompletionStage<Void> result = testWriter.requestUpdate(CONFIG_MAP_NAME, payload);

        assertNotNull(result, "Should return a CompletionStage");
    }

    @Test
    void requestUpdateShouldCallConfigMapClient() {
        ConfigMapClient mockClient = Mockito.mock(ConfigMapClient.class);
        ConfigMapWriter testWriter = new ConfigMapWriter(mockClient, NAMESPACE);

        Map<String, String> payload = Map.of("key", "value");

        CompletionStage<Void> result = testWriter.requestUpdate(CONFIG_MAP_NAME, payload);

        // Verify the method was called (async execution may not complete immediately)
        assertNotNull(result, "Should return a CompletionStage");

        verify(mockClient).createOrUpdate(eq(CONFIG_MAP_NAME), eq(NAMESPACE), eq(payload));
    }
}
