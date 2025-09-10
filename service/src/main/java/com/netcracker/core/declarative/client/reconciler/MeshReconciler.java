package com.netcracker.core.declarative.client.reconciler;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.processing.retry.GradualRetry;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import com.netcracker.core.declarative.client.rest.deprecated.MeshClientV3;
import com.netcracker.core.declarative.resources.mesh.Mesh;

@ControllerConfiguration(namespaces = Constants.WATCH_CURRENT_NAMESPACE, name = "MeshReconciler")
@SuppressWarnings("unused")
@GradualRetry(maxAttempts = -1)
public class MeshReconciler extends BaseMeshReconciler<Mesh> {

    @Inject
    public MeshReconciler(KubernetesClient client, @Named("meshDeclarativeClient") MeshClientV3 meshDeclarativeClient) {
        super(client, meshDeclarativeClient);
    }
}
