package com.netcracker.core.declarative.client.reconciler;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import jakarta.ws.rs.ServerErrorException;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import com.netcracker.core.declarative.client.rest.DeclarativeRequest;
import com.netcracker.core.declarative.resources.mesh.Mesh;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static jakarta.servlet.http.HttpServletResponse.SC_OK;
import static com.netcracker.core.declarative.resources.base.Phase.UPDATED_PHASE;

public abstract class BaseMeshReconciler<T extends Mesh> extends CoreReconciler<T> {
    private static final Logger log = LoggerFactory.getLogger(BaseMeshReconciler.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    public BaseMeshReconciler(KubernetesClient client, OkHttpClient httpClient, String baseUrl, ObjectMapper objectMapper) {
        super(client, httpClient, baseUrl);
        this.objectMapper = objectMapper;
    }

    protected BaseMeshReconciler() {
    }

    @Override
    public UpdateControl<T> reconcileInternal(T mesh) throws Exception {
        log.debug("Reconciling Mesh entity {}", mesh);
        DeclarativeRequest request = declarativeRequestBuilder(mesh);
        HttpUrl url = HttpUrl.get(baseUrl).newBuilder()
                .addPathSegment("api")
                .addPathSegment("v3")
                .addPathSegment("apply-config")
                .build();
        RequestBody body = RequestBody.create(objectMapper.writeValueAsString(request), JSON);
        Request httpRequest = new Request.Builder().url(url).post(body).build();
        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (response.code() == SC_OK) {
                return setPhaseAndReschedule(mesh, UPDATED_PHASE);
            } else {
                log.error("Unexpected status={} received from Mesh", response.code());
                throw new ServerErrorException(String.format("Unexpected status=%s received from Mesh", response.code()), 500);
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
