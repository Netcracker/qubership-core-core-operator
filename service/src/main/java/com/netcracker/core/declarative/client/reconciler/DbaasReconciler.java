package com.netcracker.core.declarative.client.reconciler;

import com.netcracker.core.declarative.client.rest.DeclarativeClient;
import com.netcracker.core.declarative.resources.dbaas.Dbaas;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.processing.retry.GradualRetry;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@Informer(namespaces = Constants.WATCH_CURRENT_NAMESPACE)
@ControllerConfiguration(name = "DBaaSReconciler")
@SuppressWarnings("unused")
@GradualRetry(maxAttempts = -1)
public class DbaasReconciler extends BaseDbaasReconciler<Dbaas> {

    @Inject
    @SuppressWarnings("unused")
    public DbaasReconciler(KubernetesClient client, @Named("dbaasDeclarativeClient") DeclarativeClient dbaasDeclarativeClient) {
        super(client, dbaasDeclarativeClient);
    }
}
