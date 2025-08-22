package com.netcracker.core.declarative.client.reconciler;

import com.netcracker.core.declarative.client.rest.DeclarativeClient;
import com.netcracker.core.declarative.client.rest.DeclarativeRequest;
import com.netcracker.core.declarative.resources.base.DeclarativeStatus;
import com.netcracker.core.declarative.resources.maas.Maas;
import io.fabric8.kubernetes.api.model.runtime.RawExtension;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ServerErrorException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@QuarkusTest
class MaaSReconcilerTest {

    @Inject
    MaaSReconciler maaSReconciler;

    @InjectMock
    @Named("maasDeclarativeClient")
    DeclarativeClient maasDeclarativeClient;

    @Test
    void reconcileInternal() throws Exception {
        when(maasDeclarativeClient.apply(eq("1"), any())).thenReturn(Response.accepted(List.of(
                "test-tracking-id",
                "test-message",
                "test-details"
        )).build());

        Maas maas = new Maas();
        maas.setSpec(new RawExtension(Map.of("test-key", "test-value")));

        when(maasDeclarativeClient.apply(eq("1"), any())).thenReturn(Response.ok().build());
        UpdateControl<Maas> maasUpdateControl = maaSReconciler.reconcileInternal(maas);
        assertTrue(maasUpdateControl.getResource().getStatus().isUpdated());

        when(maasDeclarativeClient.apply(eq("1"), any())).thenReturn(Response.serverError().build());
        assertThrows(ServerErrorException.class, () -> maaSReconciler.reconcileInternal(maas));
    }

    @Test
    void reconcilePoolingNotFoundByTrackingId() {
        Maas maas = new Maas();
        maas.setSpec(new RawExtension(Map.of("test-key", "test-value")));
        DeclarativeStatus declarativeStatus = new DeclarativeStatus();
        declarativeStatus.setTrackingId("test-tracking-id");
        maas.setStatus(declarativeStatus);

        when(maasDeclarativeClient.getStatus("1", "test-tracking-id")).thenReturn(Response.status(Response.Status.NOT_FOUND).build());
        assertThrows(NotFoundException.class, () -> maaSReconciler.reconcilePooling(maas));
    }

    @Test
    void replaceNameIfNeeded() {
        DeclarativeRequest maas = DeclarativeRequest.builder()
                .spec(createSpec(Map.of("name", "name-from-classifier")))
                .metadata(new HashMap<>() {{
                    put("name", "name-from-meta");
                }})
                .build();
        MaaSReconciler.replaceNameIfNeeded(maas);
        assertEquals("name-from-classifier", maas.getMetadata().get("name"));

        maas = DeclarativeRequest.builder()
                .spec(createSpec(Map.of("name", "")))
                .metadata(new HashMap<>() {{
                    put("name", "name-from-meta");
                }})
                .build();
        MaaSReconciler.replaceNameIfNeeded(maas);
        assertEquals("name-from-meta", maas.getMetadata().get("name"));

        maas = DeclarativeRequest.builder()
                .spec(createSpec(Map.of()))
                .metadata(new HashMap<>() {{
                    put("name", "name-from-meta");
                }})
                .build();
        MaaSReconciler.replaceNameIfNeeded(maas);
        assertEquals("name-from-meta", maas.getMetadata().get("name"));
    }

    private Map<String, Object> createSpec(Map<String, Object> classifier) {
        Map<String, Object> spec = new HashMap<>();
        spec.put("classifier", classifier);
        return spec;
    }
}