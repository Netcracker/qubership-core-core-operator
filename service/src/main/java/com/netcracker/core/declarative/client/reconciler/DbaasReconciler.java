package com.netcracker.core.declarative.client.reconciler;

import com.netcracker.core.declarative.resources.dbaas.Dbaas;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.processing.retry.GradualRetry;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import okhttp3.OkHttpClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ControllerConfiguration(informer = @Informer(namespaces = Constants.WATCH_CURRENT_NAMESPACE), name = "DBaaSReconciler")
@SuppressWarnings("unused")
@GradualRetry(maxAttempts = -1)
public class DbaasReconciler extends BaseDbaasReconciler<Dbaas> {

    @Inject
    @SuppressWarnings("unused")
    public DbaasReconciler(KubernetesClient client,
                           @Named("dbaasHttpClient") OkHttpClient httpClient,
                           @ConfigProperty(name = "api.dbaas.address") String dbaasUrl) {
        super(client, httpClient, dbaasUrl);
    }
}
