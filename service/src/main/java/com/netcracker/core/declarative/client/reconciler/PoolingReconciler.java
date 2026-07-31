package com.netcracker.core.declarative.client.reconciler;

import com.netcracker.core.declarative.client.rest.DeclarativeResponse;
import com.netcracker.core.declarative.client.rest.ProcessStatus;
import com.netcracker.core.declarative.resources.base.CoreResource;
import com.netcracker.core.declarative.resources.base.Phase;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import jakarta.ws.rs.NotFoundException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static jakarta.servlet.http.HttpServletResponse.SC_NOT_FOUND;

public abstract class PoolingReconciler<T extends CoreResource> extends CoreReconciler<T> {
    private static final Logger log = LoggerFactory.getLogger(PoolingReconciler.class);

    @SuppressWarnings("unused")
    protected PoolingReconciler() {
    }

    protected PoolingReconciler(KubernetesClient client, OkHttpClient httpClient, String baseUrl) {
        super(client, httpClient, baseUrl);
    }

    @Override
    protected UpdateControl<T> reconcilePooling(T resource) throws Exception {
        log.debug("Async reconcile for resource {}", resource);
        String trackingID = resource.getStatus().getTrackingId();
        Request request = buildStatusRequest(getApiVersion(), trackingID);
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.code() == SC_NOT_FOUND) {
                log.error("Failed to find entity with TrackingID={} on remote", trackingID);
                throw new NotFoundException(String.format("Process with TrackingID=%s not found", trackingID));
            }
            DeclarativeResponse responseBody = readEntity(response, DeclarativeResponse.class);
            return handlePoolingResponse(resource, responseBody);
        }
    }

    private UpdateControl<T> handlePoolingResponse(T resource, DeclarativeResponse responseBody) {
        responseBody.getConditions().stream()
                .filter(condition -> !condition.state().equals(ProcessStatus.NOT_STARTED))
                .forEach(condition -> buildCondition(resource, condition));
        return switch (responseBody.getStatus()) {
            case COMPLETED -> setPhaseAndReschedule(resource, Phase.UPDATED_PHASE);
            case FAILED -> setPhaseAndReschedule(resource, Phase.INVALID_CONFIGURATION);
            default -> setPhaseAndReschedule(resource, Phase.WAITING_FOR_DEPENDS);
        };
    }
}
