package com.netcracker.core.declarative.client.reconciler;

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
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class MaaSReconcilerTest {

    @Inject
    MaaSReconciler maaSReconciler;

    @InjectMock
    @Named("maasHttpClient")
    OkHttpClient maasHttpClient;

    @Test
    void reconcileInternal() throws Exception {
        Maas maas = new Maas();
        maas.setSpec(new RawExtension(Map.of("test-key", "test-value")));

        OkHttpMocks.stub(maasHttpClient, 200, null);
        UpdateControl<Maas> maasUpdateControl = maaSReconciler.reconcileInternal(maas);
        assertTrue(maasUpdateControl.getResource().get().getStatus().isUpdated());

        OkHttpMocks.stub(maasHttpClient, 500, null);
        assertThrows(ServerErrorException.class, () -> maaSReconciler.reconcileInternal(maas));
    }

    @Test
    void reconcilePoolingNotFoundByTrackingId() {
        Maas maas = new Maas();
        maas.setSpec(new RawExtension(Map.of("test-key", "test-value")));
        DeclarativeStatus declarativeStatus = new DeclarativeStatus();
        declarativeStatus.setTrackingId("test-tracking-id");
        maas.setStatus(declarativeStatus);

        OkHttpMocks.stub(maasHttpClient, 404, null);
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
