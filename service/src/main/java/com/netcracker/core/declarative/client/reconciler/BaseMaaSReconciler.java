package com.netcracker.core.declarative.client.reconciler;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.lang.StringUtils;
import com.netcracker.core.declarative.client.rest.DeclarativeClient;
import com.netcracker.core.declarative.client.rest.DeclarativeRequest;
import com.netcracker.core.declarative.resources.maas.Maas;

import java.util.Map;


public abstract class BaseMaaSReconciler<T extends Maas> extends PoolingReconciler<T> {
    public static final String CLASSIFIER_PROPERTY = "classifier";
    public static final String CLASSIFIER_NAME_PROPERTY = "name";

    public BaseMaaSReconciler(KubernetesClient client, DeclarativeClient maasDeclarativeClient) {
        super(client, maasDeclarativeClient);
    }

    protected BaseMaaSReconciler() {
    }

    @Override
    protected DeclarativeRequest declarativeRequestBuilder(T resource) {
        DeclarativeRequest declarativeRequest = super.declarativeRequestBuilder(resource);
        replaceNameIfNeeded(declarativeRequest);
        return declarativeRequest;
    }

    protected static void replaceNameIfNeeded(DeclarativeRequest resource) {
        Map<String, Object> spec = (Map<String, Object>) resource.getSpec();
        if (spec.containsKey(CLASSIFIER_PROPERTY)) {
            Map<String, Object> classifier = (Map<String, Object>) spec.get(CLASSIFIER_PROPERTY);
            if (classifier.containsKey(CLASSIFIER_NAME_PROPERTY) && StringUtils.isNotEmpty(classifier.get(CLASSIFIER_NAME_PROPERTY).toString())) {
                spec.remove(CLASSIFIER_PROPERTY);
                resource.getMetadata().put("name", classifier.get(CLASSIFIER_NAME_PROPERTY).toString());
            }
        }
    }
}