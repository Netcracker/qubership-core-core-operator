package com.netcracker.core.declarative.client.k8s;

import io.fabric8.kubernetes.api.model.*;
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
    public static final String PART_OF_CLOUD_CORE = "Cloud-Core";

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
        createOrUpdate(name, namespace, data, null);
    }

    /**
     * Creates or updates a ConfigMap if it is managed by core-operator (or does not exist yet).
     * Merges existing labels with core-operator ownership labels.
     */
    public void createOrUpdate(String name,
                               String namespace,
                               Map<String, String> data,
                               HasMetadata owner) {
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

        OwnerReference ownerReference = null;
        if (owner != null) {
            ownerReference = new OwnerReferenceBuilder()
                    .withApiVersion(owner.getApiVersion())
                    .withKind(owner.getKind())
                    .withName(owner.getMetadata().getName())
                    .withUid(owner.getMetadata().getUid())
                    .withController(true)
                    .withBlockOwnerDeletion(false)
                    .build();
        }

        ConfigMap configMap = new ConfigMapBuilder()
                .withNewMetadata()
                .withName(name)
                .withNamespace(namespace)
                .withLabels(effectiveLabels)
                .withOwnerReferences(ownerReference)
                .endMetadata()
                .withData(data)
                .build();

        client.configMaps()
                .inNamespace(namespace)
                .resource(configMap)
                .fieldManager(MANAGED_BY_CORE_OPERATOR)
                .serverSideApply();
    }

    private Map<String, String> resolveConfigMapLabels(ConfigMap existingConfigMap) {
        Map<String, String> mergedLabels = new HashMap<>();
        if (existingConfigMap != null
                && existingConfigMap.getMetadata() != null
                && existingConfigMap.getMetadata().getLabels() != null) {
            mergedLabels.putAll(existingConfigMap.getMetadata().getLabels());
        }

        mergedLabels.put(LABEL_PART_OF, PART_OF_CLOUD_CORE);
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

        log.info("VLLA existingConfigMap = {}", existingConfigMap);

        return shouldBeManagedByCoreOperator(existingConfigMap);
    }

    boolean shouldBeManagedByCoreOperator(ConfigMap existingConfigMap) {
        return Optional.ofNullable(existingConfigMap)
                .map(ConfigMap::getMetadata)
                .map(ObjectMeta::getLabels)
                .map(labels -> labels.get(LABEL_MANAGED_BY))
                .map(MANAGED_BY_CORE_OPERATOR::equals)
                .orElse(true);
    }

    private ConfigMap getConfigMap(String name, String namespace) {
        return client.configMaps()
                .inNamespace(namespace)
                .withName(name)
                .get();
    }
}
