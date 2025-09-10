package com.netcracker.core.declarative.client.reconciler;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.processing.retry.GradualRetry;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import com.netcracker.core.declarative.client.rest.DeclarativeClient;
import com.netcracker.core.declarative.resources.maas.Maas;

@ControllerConfiguration(namespaces = Constants.WATCH_CURRENT_NAMESPACE, name = "MaaSReconciler")
@SuppressWarnings("unused")
@GradualRetry(maxAttempts = -1)
public class MaaSReconciler extends BaseMaaSReconciler<Maas> {

    @Inject
    @SuppressWarnings("unused")
    public MaaSReconciler(KubernetesClient client, @Named("maasDeclarativeClient") DeclarativeClient maasDeclarativeClient) {
        super(client, maasDeclarativeClient);
    }
}
