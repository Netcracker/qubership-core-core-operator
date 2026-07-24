package com.netcracker.core.declarative.client.reconciler;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.processing.retry.GradualRetry;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import okhttp3.OkHttpClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import com.netcracker.core.declarative.resources.mesh.Mesh;

@ControllerConfiguration(informer = @Informer(namespaces = Constants.WATCH_CURRENT_NAMESPACE), name = "MeshReconciler")
@SuppressWarnings("unused")
@GradualRetry(maxAttempts = -1)
public class MeshReconciler extends BaseMeshReconciler<Mesh> {

    @Inject
    public MeshReconciler(KubernetesClient client,
                          @Named("meshHttpClient") OkHttpClient httpClient,
                          @ConfigProperty(name = "mesh.internal.address") String meshUrl,
                          ObjectMapper objectMapper) {
        super(client, httpClient, meshUrl, objectMapper);
    }
}
