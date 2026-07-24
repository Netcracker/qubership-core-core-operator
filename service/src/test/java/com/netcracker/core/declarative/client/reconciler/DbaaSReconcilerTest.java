package com.netcracker.core.declarative.client.reconciler;

import com.netcracker.core.declarative.resources.base.DeclarativeStatus;
import com.netcracker.core.declarative.resources.dbaas.Dbaas;
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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class DbaaSReconcilerTest {

    @Inject
    DbaasReconciler dbaasReconciler;

    @InjectMock
    @Named("dbaasHttpClient")
    OkHttpClient dbaasHttpClient;

    @Test
    void reconcileInternal() throws Exception {
        Dbaas dbaas = new Dbaas();
        dbaas.setSpec(new RawExtension(Map.of("test-key", "test-value")));

        OkHttpMocks.stub(dbaasHttpClient, 200, null);
        UpdateControl<Dbaas> dbaasUpdateControl = dbaasReconciler.reconcileInternal(dbaas);
        assertTrue(dbaasUpdateControl.getResource().get().getStatus().isUpdated());

        OkHttpMocks.stub(dbaasHttpClient, 500, null);
        assertThrows(ServerErrorException.class, () -> dbaasReconciler.reconcileInternal(dbaas));
    }

    @Test
    void reconcilePoolingNotFound() {
        Dbaas dbaas = new Dbaas();
        dbaas.setSpec(new RawExtension(Map.of("test-key", "test-value")));
        DeclarativeStatus declarativeStatus = new DeclarativeStatus();
        declarativeStatus.setTrackingId("test-tracking-id");
        dbaas.setStatus(declarativeStatus);

        OkHttpMocks.stub(dbaasHttpClient, 404, null);
        assertThrows(NotFoundException.class, () -> dbaasReconciler.reconcilePooling(dbaas));
    }
}
