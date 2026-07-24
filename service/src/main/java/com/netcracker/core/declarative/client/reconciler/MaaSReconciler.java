package com.netcracker.core.declarative.client.reconciler;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.processing.retry.GradualRetry;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import okhttp3.OkHttpClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import com.netcracker.core.declarative.resources.maas.Maas;

@ControllerConfiguration(informer = @Informer(namespaces = Constants.WATCH_CURRENT_NAMESPACE), name = "MaaSReconciler")
@SuppressWarnings("unused")
@GradualRetry(maxAttempts = -1)
public class MaaSReconciler extends BaseMaaSReconciler<Maas> {

    @Inject
    @SuppressWarnings("unused")
    public MaaSReconciler(KubernetesClient client,
                          @Named("maasHttpClient") OkHttpClient httpClient,
                          @ConfigProperty(name = "maas.internal.address") String maasUrl) {
        super(client, httpClient, maasUrl);
    }
}
