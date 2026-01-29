package com.netcracker.core.declarative.service.composite;

import com.netcracker.core.declarative.client.k8s.ConfigMapClient;
import io.fabric8.kubernetes.api.model.HasMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    private HasMetadata owner;
    private ConfigMapWriter writer;

    @BeforeEach
    void setUp() {
        configMapClient = mock(ConfigMapClient.class);
        owner = mock(HasMetadata.class);
        writer = new ConfigMapWriter(configMapClient, NAMESPACE);
    }

    @Test
    void requestUpdateShouldCallConfigMapClient() {
        Map<String, String> payload = Map.of("key", "value");

        CompletionStage<Void> result = writer.requestUpdate(CONFIG_MAP_NAME, payload, owner);

        assertThat(result).isNotNull();
        verify(configMapClient).createOrUpdate(eq(CONFIG_MAP_NAME), eq(NAMESPACE), eq(payload), eq(owner));
    }

    @Test
    void requestUpdateShouldThrowOnNullConfigMapName() {
        Map<String, String> payload = Map.of("key", "value");

        assertThatThrownBy(() -> writer.requestUpdate(null, payload, owner))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("configMapName");
    }

    @Test
    void requestUpdateShouldThrowOnNullPayload() {
        assertThatThrownBy(() -> writer.requestUpdate(CONFIG_MAP_NAME, null, owner))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("payload");
    }
}
