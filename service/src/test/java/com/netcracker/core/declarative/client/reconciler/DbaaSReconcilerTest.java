package com.netcracker.core.declarative.client.reconciler;

import com.netcracker.core.declarative.client.rest.DeclarativeClient;
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
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@QuarkusTest
class DbaaSReconcilerTest {

    @Inject
    DbaasReconciler dbaasReconciler;

    @InjectMock
    @Named("dbaasDeclarativeClient")
    DeclarativeClient dbaasDeclarativeClient;

    @Test
    void reconcileInternal() throws Exception {
        when(dbaasDeclarativeClient.apply(eq("1"), any())).thenReturn(Response.accepted(List.of(
                "test-tracking-id",
                "test-message",
                "test-details"
        )).build());

        Dbaas dbaas = new Dbaas();
        dbaas.setSpec(new RawExtension(Map.of("test-key", "test-value")));

        when(dbaasDeclarativeClient.apply(eq("1"), any())).thenReturn(Response.ok().build());
        UpdateControl<Dbaas> dbaasUpdateControl = dbaasReconciler.reconcileInternal(dbaas);
        assertTrue(dbaasUpdateControl.getResource().getStatus().isUpdated());

        when(dbaasDeclarativeClient.apply(eq("1"), any())).thenReturn(Response.serverError().build());
        assertThrows(ServerErrorException.class, () -> dbaasReconciler.reconcileInternal(dbaas));
    }

    @Test
    void reconcilePoolingNotFound() {
        Dbaas dbaas = new Dbaas();
        dbaas.setSpec(new RawExtension(Map.of("test-key", "test-value")));
        DeclarativeStatus declarativeStatus = new DeclarativeStatus();
        declarativeStatus.setTrackingId("test-tracking-id");
        dbaas.setStatus(declarativeStatus);

        when(dbaasDeclarativeClient.getStatus("1", "test-tracking-id")).thenReturn(Response.status(Response.Status.NOT_FOUND).build());
        assertThrows(NotFoundException.class, () -> dbaasReconciler.reconcilePooling(dbaas));
    }
}