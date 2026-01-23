package com.netcracker.core.declarative.client.k8s;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Client for managing ConfigMaps owned by core-operator via server-side apply.
 * Respects ownership: skips updates for ConfigMaps managed by other controllers
 * (determined by {@code app.kubernetes.io/managed-by} label).
 */
@ApplicationScoped
@Slf4j
public class ConfigMapClient {
    public static final String LABEL_PART_OF = "app.kubernetes.io/part-of";
    public static final String LABEL_MANAGED_BY = "app.kubernetes.io/managed-by";
    public static final String MANAGED_BY_CORE_OPERATOR = "core-operator";
    public static final String CLOUD_CORE_PART_OF = "Cloud-Core";

    private final KubernetesClient client;

    @Inject
    public ConfigMapClient(KubernetesClient client) {
        this.client = client;
    }

    /**
     * Creates or updates a ConfigMap if it is managed by core-operator (or does not exist yet).
     * Merges existing labels with core-operator ownership labels.
     */
    public void createOrUpdate(String name,
                               String namespace,
                               Map<String, String> data) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(data, "data");

        log.debug("Start creating or updating config map with name = {}", name);

        ConfigMap existingConfigMap = getConfigMap(name, namespace);

        if (!shouldBeManagedByCoreOperator(existingConfigMap)) {
            log.info("Config map '{}' in namespace '{}' is not managed by '{}'. Skipping update.",
                    name, namespace, MANAGED_BY_CORE_OPERATOR);
            return;
        }

        Map<String, String> effectiveLabels = resolveConfigMapLabels(existingConfigMap);

        ConfigMap configMap = new ConfigMapBuilder()
                .withNewMetadata()
                .withName(name)
                .withNamespace(namespace)
                .withLabels(effectiveLabels)
                .endMetadata()
                .withData(data)
                .build();

        client.configMaps()
                .inNamespace(namespace)
                .resource(configMap)
                .fieldManager("core-operator")
                .serverSideApply();
    }

    private Map<String, String> resolveConfigMapLabels(ConfigMap existingConfigMap) {
        Map<String, String> mergedLabels = new HashMap<>();
        if (existingConfigMap != null
            && existingConfigMap.getMetadata() != null
            && existingConfigMap.getMetadata().getLabels() != null) {
            mergedLabels.putAll(existingConfigMap.getMetadata().getLabels());
        }

        mergedLabels.put(LABEL_PART_OF, CLOUD_CORE_PART_OF);
        mergedLabels.put(LABEL_MANAGED_BY, MANAGED_BY_CORE_OPERATOR);

        return mergedLabels;
    }

    /**
     * Returns {@code true} if the ConfigMap does not exist, has no {@code managed-by} label,
     * or is explicitly managed by core-operator.
     */
    public boolean shouldBeManagedByCoreOperator(String name, String namespace) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(namespace, "namespace");

        ConfigMap existingConfigMap = getConfigMap(name, namespace);

        return shouldBeManagedByCoreOperator(existingConfigMap);
    }

    boolean shouldBeManagedByCoreOperator(ConfigMap existingConfigMap) {
        if (existingConfigMap == null) {
            return true;
        }
        String managedBy = Optional.ofNullable(existingConfigMap.getMetadata())
                .map(ObjectMeta::getLabels)
                .map(labels -> labels.get(LABEL_MANAGED_BY))
                .orElse(null);
        if (managedBy == null) {
            //If no one manages - core-operator manages.
            return true;
        }
        return MANAGED_BY_CORE_OPERATOR.equals(managedBy);
    }

    private ConfigMap getConfigMap(String name, String namespace) {
        return client.configMaps()
                .inNamespace(namespace)
                .withName(name)
                .get();
    }
}
