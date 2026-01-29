package com.netcracker.core.declarative.client.k8s;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ConfigMapList;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.*;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfigMapClientTest {

    private static final String CONFIG_MAP_NAME = "test-config-map";
    private static final String NAMESPACE = "test-namespace";

    @Test
    void shouldMergeLabelsAndSetManagedByCoreOperator() {
        KubernetesClient client = mock(KubernetesClient.class);
        ConfigMap existingConfigMap = new ConfigMapBuilder()
                .withNewMetadata()
                .withLabels(Map.of("existing", "label"))
                .endMetadata()
                .build();

        ApplyResult applyResult = prepareConfigMapMocks(client, existingConfigMap);

        ConfigMapClient configMapClient = new ConfigMapClient(client);
        Map<String, String> data = Map.of("key", "value");

        configMapClient.createOrUpdate(CONFIG_MAP_NAME, NAMESPACE, data);

        ConfigMap appliedConfigMap = applyResult.appliedConfigMapRef().get();
        assertNotNull(appliedConfigMap);
        assertEquals(data, appliedConfigMap.getData());

        Map<String, String> resultingLabels = appliedConfigMap.getMetadata().getLabels();
        assertEquals(3, resultingLabels.size());
        assertEquals("label", resultingLabels.get("existing"));
        assertEquals("Cloud-Core", resultingLabels.get("app.kubernetes.io/part-of"));
        assertEquals("core-operator", resultingLabels.get("app.kubernetes.io/managed-by"));
    }

    @Test
    void shouldSkipUpdateWhenManagedByTopologyOperator() {
        KubernetesClient client = mock(KubernetesClient.class);
        ConfigMap existingConfigMap = new ConfigMapBuilder()
                .withNewMetadata()
                .withLabels(Map.of("app.kubernetes.io/managed-by", "topology-operator"))
                .endMetadata()
                .build();

        ApplyResult applyResult = prepareConfigMapMocks(client, existingConfigMap);

        ConfigMapClient configMapClient = new ConfigMapClient(client);
        configMapClient.createOrUpdate(CONFIG_MAP_NAME, NAMESPACE, Map.of("key", "value"));

        assertFalse(applyResult.wasApplied().get());
    }

    private ApplyResult prepareConfigMapMocks(KubernetesClient client, ConfigMap existingConfigMap) {
        @SuppressWarnings("unchecked")
        MixedOperation<ConfigMap, ConfigMapList, Resource<ConfigMap>> configMapOperation = mock(MixedOperation.class);
        @SuppressWarnings("unchecked")
        NonNamespaceOperation<ConfigMap, ConfigMapList, Resource<ConfigMap>> namespacedConfigMapOperation = mock(NonNamespaceOperation.class);
        @SuppressWarnings("unchecked")
        Resource<ConfigMap> namedConfigMapResource = mock(Resource.class);
        @SuppressWarnings("unchecked")
        Resource<ConfigMap> configMapResource = mock(Resource.class);

        when(client.configMaps()).thenReturn(configMapOperation);
        when(configMapOperation.inNamespace(NAMESPACE)).thenReturn(namespacedConfigMapOperation);
        when(namespacedConfigMapOperation.withName(CONFIG_MAP_NAME)).thenReturn(namedConfigMapResource);
        when(namedConfigMapResource.get()).thenReturn(existingConfigMap);

        AtomicReference<ConfigMap> appliedConfigMapRef = new AtomicReference<>();
        AtomicBoolean wasApplied = new AtomicBoolean(false);
        when(namespacedConfigMapOperation.resource(Mockito.any(ConfigMap.class))).thenAnswer(invocation -> {
            ConfigMap configMap = invocation.getArgument(0);
            appliedConfigMapRef.set(configMap);
            return configMapResource;
        });
        when(configMapResource.fieldManager(anyString())).thenReturn(configMapResource);
        when(configMapResource.serverSideApply()).thenAnswer(invocation -> {
            wasApplied.set(true);
            return null;
        });

        return new ApplyResult(appliedConfigMapRef, wasApplied);
    }

    private record ApplyResult(AtomicReference<ConfigMap> appliedConfigMapRef, AtomicBoolean wasApplied) {}

    @Test
    void shouldBeManagedByCoreOperator_returnsTrue_whenConfigMapIsNull() {
        KubernetesClient client = mock(KubernetesClient.class);
        ConfigMapClient configMapClient = new ConfigMapClient(client);

        assertTrue(configMapClient.shouldBeManagedByCoreOperator((ConfigMap) null));
    }

    @Test
    void shouldBeManagedByCoreOperator_returnsTrue_whenMetadataIsNull() {
        KubernetesClient client = mock(KubernetesClient.class);
        ConfigMapClient configMapClient = new ConfigMapClient(client);

        ConfigMap configMap = new ConfigMap();
        configMap.setMetadata(null);

        assertTrue(configMapClient.shouldBeManagedByCoreOperator(configMap));
    }

    @Test
    void shouldBeManagedByCoreOperator_returnsTrue_whenLabelsAreNull() {
        KubernetesClient client = mock(KubernetesClient.class);
        ConfigMapClient configMapClient = new ConfigMapClient(client);

        ConfigMap configMap = new ConfigMapBuilder()
                .withNewMetadata()
                .endMetadata()
                .build();

        assertTrue(configMapClient.shouldBeManagedByCoreOperator(configMap));
    }

    @Test
    void shouldBeManagedByCoreOperator_returnsTrue_whenLabelsAreEmpty() {
        KubernetesClient client = mock(KubernetesClient.class);
        ConfigMapClient configMapClient = new ConfigMapClient(client);

        ConfigMap configMap = new ConfigMapBuilder()
                .withNewMetadata()
                .withLabels(Collections.emptyMap())
                .endMetadata()
                .build();

        assertTrue(configMapClient.shouldBeManagedByCoreOperator(configMap));
    }

    @Test
    void shouldBeManagedByCoreOperator_returnsTrue_whenManagedByLabelIsAbsent() {
        KubernetesClient client = mock(KubernetesClient.class);
        ConfigMapClient configMapClient = new ConfigMapClient(client);

        ConfigMap configMap = new ConfigMapBuilder()
                .withNewMetadata()
                .withLabels(Map.of("some-other-label", "some-value"))
                .endMetadata()
                .build();

        assertTrue(configMapClient.shouldBeManagedByCoreOperator(configMap));
    }

    @Test
    void shouldBeManagedByCoreOperator_returnsTrue_whenManagedByCoreOperator() {
        KubernetesClient client = mock(KubernetesClient.class);
        ConfigMapClient configMapClient = new ConfigMapClient(client);

        ConfigMap configMap = new ConfigMapBuilder()
                .withNewMetadata()
                .withLabels(Map.of(ConfigMapClient.LABEL_MANAGED_BY, ConfigMapClient.MANAGED_BY_CORE_OPERATOR))
                .endMetadata()
                .build();

        assertTrue(configMapClient.shouldBeManagedByCoreOperator(configMap));
    }

    @Test
    void shouldBeManagedByCoreOperator_returnsFalse_whenManagedByAnotherOperator() {
        KubernetesClient client = mock(KubernetesClient.class);
        ConfigMapClient configMapClient = new ConfigMapClient(client);

        ConfigMap configMap = new ConfigMapBuilder()
                .withNewMetadata()
                .withLabels(Map.of(ConfigMapClient.LABEL_MANAGED_BY, "another-operator"))
                .endMetadata()
                .build();

        assertFalse(configMapClient.shouldBeManagedByCoreOperator(configMap));
    }
}
