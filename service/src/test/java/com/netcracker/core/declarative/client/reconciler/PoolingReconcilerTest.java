package com.netcracker.core.declarative.client.reconciler;

import com.netcracker.core.declarative.client.rest.Condition;
import com.netcracker.core.declarative.client.rest.DeclarativeClient;
import com.netcracker.core.declarative.client.rest.DeclarativeResponse;
import com.netcracker.core.declarative.client.rest.ProcessStatus;
import com.netcracker.core.declarative.resources.base.DeclarativeStatus;
import com.netcracker.core.declarative.resources.base.Phase;
import com.netcracker.core.declarative.resources.maas.Maas;
import io.fabric8.kubernetes.api.model.runtime.RawExtension;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@QuarkusTest
class PoolingReconcilerTest {
    @Inject
    MaaSReconciler maaSReconciler;

    @InjectMock
    @Named("maasDeclarativeClient")
    DeclarativeClient maasDeclarativeClient;

    @Test
    void reconcilePoolingFoundByTrackingId() throws Exception {
        Maas maas = new Maas();
        maas.setSpec(new RawExtension(Map.of("test-key", "test-value")));
        DeclarativeStatus declarativeStatus = new DeclarativeStatus();
        declarativeStatus.setTrackingId("test-tracking-id");
        maas.setStatus(declarativeStatus);

        DeclarativeResponse resp = new DeclarativeResponse();
        resp.setStatus(ProcessStatus.COMPLETED);
        resp.setTrackingId("test-tracking-id");
        List<Condition> conditions = new ArrayList<>();
        Condition condition = new Condition("conditionType", ProcessStatus.COMPLETED, "reason", "message");
        conditions.add(condition);
        resp.setConditions(conditions);
        when(maasDeclarativeClient.getStatus("1", "test-tracking-id")).thenReturn(Response.status(Response.Status.OK).entity(resp).build());

        maaSReconciler.reconcilePooling(maas);
        assertEquals(Phase.UPDATED_PHASE, maas.getStatus().getPhase());
        assertEquals(ProcessStatus.COMPLETED, maas.getStatus().getConditions().get("conditionType").getState());
    }

    @Test
    void reconcilePoolingFoundByTrackingIdReturnsError() throws Exception {
        Maas maas = new Maas();
        maas.setSpec(new RawExtension(Map.of("test-key", "test-value")));
        DeclarativeStatus declarativeStatus = new DeclarativeStatus();
        declarativeStatus.setTrackingId("test-tracking-id");
        maas.setStatus(declarativeStatus);

        DeclarativeResponse resp = new DeclarativeResponse();
        resp.setStatus(ProcessStatus.FAILED);
        resp.setTrackingId("test-tracking-id");
        List<Condition> conditions = new ArrayList<>();
        Condition condition = new Condition("conditionType", ProcessStatus.FAILED, "reason", "message");
        conditions.add(condition);
        resp.setConditions(conditions);
        when(maasDeclarativeClient.getStatus("1", "test-tracking-id")).thenReturn(Response.status(Response.Status.OK).entity(resp).build());

        maaSReconciler.reconcilePooling(maas);
        assertEquals(Phase.INVALID_CONFIGURATION, maas.getStatus().getPhase());
        assertEquals(ProcessStatus.FAILED, maas.getStatus().getConditions().get("conditionType").getState());
    }

    @Test
    void reconcilePoolingFoundByTrackingIdWaiting() throws Exception {
        Maas maas = new Maas();
        maas.setSpec(new RawExtension(Map.of("test-key", "test-value")));
        DeclarativeStatus declarativeStatus = new DeclarativeStatus();
        declarativeStatus.setTrackingId("test-tracking-id");
        maas.setStatus(declarativeStatus);

        DeclarativeResponse resp = new DeclarativeResponse();
        resp.setStatus(ProcessStatus.IN_PROGRESS);
        resp.setTrackingId("test-tracking-id");
        List<Condition> conditions = new ArrayList<>();
        Condition condition = new Condition("conditionType", ProcessStatus.IN_PROGRESS, "reason", "message");
        conditions.add(condition);
        resp.setConditions(conditions);
        when(maasDeclarativeClient.getStatus("1", "test-tracking-id")).thenReturn(Response.status(Response.Status.OK).entity(resp).build());

        maaSReconciler.reconcilePooling(maas);
        assertEquals(Phase.WAITING_FOR_DEPENDS, maas.getStatus().getPhase());
        assertEquals(ProcessStatus.IN_PROGRESS, maas.getStatus().getConditions().get("conditionType").getState());
    }
}
