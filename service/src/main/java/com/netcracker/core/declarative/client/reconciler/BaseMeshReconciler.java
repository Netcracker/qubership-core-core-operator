package com.netcracker.core.declarative.client.reconciler;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import jakarta.ws.rs.ServerErrorException;
import jakarta.ws.rs.core.Response;
import com.netcracker.core.declarative.client.rest.DeclarativeRequest;
import com.netcracker.core.declarative.client.rest.deprecated.MeshClientV3;
import com.netcracker.core.declarative.resources.mesh.Mesh;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static jakarta.servlet.http.HttpServletResponse.SC_OK;
import static com.netcracker.core.declarative.resources.base.Phase.UPDATED_PHASE;

public abstract class BaseMeshReconciler<T extends Mesh> extends CoreReconciler<T> {
    private static final Logger log = LoggerFactory.getLogger(BaseMeshReconciler.class);
    private MeshClientV3 meshClient;

    public BaseMeshReconciler(KubernetesClient client, MeshClientV3 meshDeclarativeClient) {
        super(client);
        this.meshClient = meshDeclarativeClient;
    }

    protected BaseMeshReconciler() {
    }

    @Override
    public UpdateControl<T> reconcileInternal(T mesh) throws Exception {
        log.debug("Reconciling Mesh entity {}", mesh);
        DeclarativeRequest request = declarativeRequestBuilder(mesh);
        try (Response response = meshClient.applyConfig(request)) {
            if (response.getStatusInfo().getStatusCode() == SC_OK) {
                return setPhaseAndReschedule(mesh, UPDATED_PHASE);
            } else {
                log.error("Unexpected status={} received from Mesh", response.getStatusInfo().getStatusCode());
                throw new ServerErrorException(String.format("Unexpected status=%s received from Mesh", response.getStatusInfo().getStatusCode()), 500);
            }
        }
    }

    /**
     * Duplicates {@link CoreReconciler} logic until we transition to unified API with Mesh
     */
    @Override
    protected DeclarativeRequest declarativeRequestBuilder(T resource) {
        HashMap<String, Object> meta = new HashMap<>();
        meta.put("name", resource.getMetadata().getName());
        meta.put("namespace", resource.getMetadata().getNamespace());
        meta.put("microserviceName", getLabelOrAlternative(resource, "app.kubernetes.io/name", "app.kubernetes.io/instance"));

        DeclarativeRequest.DeclarativeRequestBuilder builder = DeclarativeRequest.builder()
                .apiVersion("nc.core.mesh/v3")
                .kind(resource.getSubKind())
                .metadata(meta);

        Map<String, Object> spec = (Map<String, Object>) resource.getSpec().getValue();
        return resource.getSubKind().equals("RoutesDrop") ?
                builder.spec(spec.get("entities")).build() :
                builder.spec(spec).build();
    }
}
