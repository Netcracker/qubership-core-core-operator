package com.netcracker.core.declarative.client.reconciler;

import com.netcracker.core.declarative.client.rest.Condition;
import com.netcracker.core.declarative.client.rest.DeclarativeClient;
import com.netcracker.core.declarative.client.rest.DeclarativeRequest;
import com.netcracker.core.declarative.client.rest.DeclarativeResponse;
import com.netcracker.core.declarative.client.rest.ProcessStatus;
import com.netcracker.core.declarative.resources.base.CoreCondition;
import com.netcracker.core.declarative.resources.base.CoreResource;
import com.netcracker.core.declarative.resources.maas.Maas;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.runtime.RawExtension;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.NamespaceableResource;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import jakarta.inject.Named;
import jakarta.ws.rs.ServerErrorException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import com.netcracker.cloud.core.error.rest.tmf.TmfError;
import com.netcracker.cloud.core.error.rest.tmf.TmfErrorResponse;
import org.slf4j.MDC;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.netcracker.core.declarative.client.constants.Constants.KIND;
import static com.netcracker.core.declarative.client.constants.Constants.PHASE;
import static com.netcracker.core.declarative.client.constants.Constants.MESSAGE_UNKNOWN;
import static com.netcracker.core.declarative.client.constants.Constants.RESOURCE_NAME;
import static com.netcracker.core.declarative.client.constants.Constants.SESSION_ID_KEY;
import static com.netcracker.core.declarative.client.constants.Constants.SUB_KIND;
import static com.netcracker.core.declarative.client.constants.Constants.TYPE_UNKNOWN;
import static com.netcracker.core.declarative.client.constants.Constants.VALIDATED_STEP_NAME;
import static com.netcracker.core.declarative.client.constants.Constants.X_REQUEST_ID;
import static com.netcracker.core.declarative.client.rest.ProcessStatus.COMPLETED;
import static com.netcracker.core.declarative.resources.base.Phase.BACKING_OFF;
import static com.netcracker.core.declarative.resources.base.Phase.INVALID_CONFIGURATION;
import static com.netcracker.core.declarative.resources.base.Phase.UNKNOWN;
import static com.netcracker.core.declarative.resources.base.Phase.UPDATED_PHASE;
import static com.netcracker.core.declarative.resources.base.Phase.UPDATING;
import static com.netcracker.core.declarative.resources.base.Phase.WAITING_FOR_DEPENDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@QuarkusTest
class CoreReconcilerTest {
    private static final String SESSION_ID_LABEL = "deployment.qubership.org/sessionId";
    private static final String NAME_LABEL = "app.kubernetes.io/name";
    private static final String OLD_LABEL = "app.kubernetes.io/instance";
    @InjectSpy
    MaaSReconciler maaSReconciler;

    @InjectMock
    @Named("maasDeclarativeClient")
    DeclarativeClient maasDeclarativeClient;

    @Test
    void reconcileInternalTest() throws Exception {
        when(maasDeclarativeClient.apply(eq("1"), any())).thenReturn(Response.accepted(List.of(
                "test-tracking-id",
                "test-message",
                "test-details"
        )).build());

        Maas maas = new Maas();
        maas.setSpec(new RawExtension(Map.of("test-key", "test-value")));
        ObjectMeta maasMetadata = new ObjectMeta();
        maasMetadata.setName("maas1");
        maas.setMetadata(maasMetadata);

        when(maasDeclarativeClient.apply(eq("1"), any())).thenReturn(Response.ok().build());
        UpdateControl<Maas> maasUpdateControl = maaSReconciler.reconcileInternal(maas);
        assertTrue(maasUpdateControl.getResource().getStatus().isUpdated());

        when(maasDeclarativeClient.apply(eq("1"), any())).thenReturn(Response.serverError().build());
        assertThrows(ServerErrorException.class, () -> maaSReconciler.reconcileInternal(maas));

        DeclarativeResponse resp = new DeclarativeResponse();
        resp.setStatus(ProcessStatus.IN_PROGRESS);
        resp.setTrackingId("test-tracking-id");
        List<Condition> conditions = new ArrayList<>();
        Condition condition = new Condition("conditionType", ProcessStatus.IN_PROGRESS, "reason", "message");
        conditions.add(condition);
        resp.setConditions(conditions);
        when(maasDeclarativeClient.apply(eq("1"), any())).thenReturn(Response.accepted().entity(resp).build());
        maasUpdateControl = maaSReconciler.reconcileInternal(maas);
        assertEquals(WAITING_FOR_DEPENDS, maasUpdateControl.getResource().getStatus().getPhase());
        assertEquals(2000L, (long) maasUpdateControl.getScheduleDelay().get());

        //test retry timeout
        when(maasDeclarativeClient.apply(eq("1"), any())).thenReturn(Response.accepted().entity(resp).build());
        maasUpdateControl = maaSReconciler.reconcileInternal(maas);
        assertEquals(WAITING_FOR_DEPENDS, maasUpdateControl.getResource().getStatus().getPhase());
        assertEquals(4000L, (long) maasUpdateControl.getScheduleDelay().get());

        //test another maas has its own timeout
        Maas anotherMaas = new Maas();
        anotherMaas.setSpec(new RawExtension(Map.of("other-test-key", "other-test-value")));
        ObjectMeta anotherMaasMetadata = new ObjectMeta();
        anotherMaasMetadata.setName("maas2");
        anotherMaas.setMetadata(anotherMaasMetadata);
        when(maasDeclarativeClient.apply(eq("1"), any())).thenReturn(Response.accepted().entity(resp).build());
        maasUpdateControl = maaSReconciler.reconcileInternal(anotherMaas);
        assertEquals(WAITING_FOR_DEPENDS, maasUpdateControl.getResource().getStatus().getPhase());
        assertEquals(1000L, (long) maasUpdateControl.getScheduleDelay().get());

        when(maasDeclarativeClient.apply(eq("1"), any())).thenReturn(Response.accepted().entity(resp).build());
        maasUpdateControl = maaSReconciler.reconcileInternal(anotherMaas);
        assertEquals(WAITING_FOR_DEPENDS, maasUpdateControl.getResource().getStatus().getPhase());
        assertEquals(2000L, (long) maasUpdateControl.getScheduleDelay().get());

        //test retry timeout set to 1s
        when(maasDeclarativeClient.apply(eq("1"), any())).thenReturn(Response.ok().entity(resp).build());
        maasUpdateControl = maaSReconciler.reconcileInternal(maas);
        assertEquals(UPDATED_PHASE, maasUpdateControl.getResource().getStatus().getPhase());
        assertEquals(1000L, (long) maasUpdateControl.getScheduleDelay().get());

        when(maasDeclarativeClient.apply(eq("1"), any())).thenReturn(Response.ok().entity(resp).build());
        maasUpdateControl = maaSReconciler.reconcileInternal(anotherMaas);
        assertEquals(UPDATED_PHASE, maasUpdateControl.getResource().getStatus().getPhase());
        assertEquals(1000L, (long) maasUpdateControl.getScheduleDelay().get());
    }

    @Test
    void labelFallbackTest() throws Exception {
        //1. test new label
        Maas maas = new Maas();
        maas.setSpec(new RawExtension(Map.of("test-key", "test-value")));
        maas.setSubKind("TopicTemplate");
        Map<String, String> labels = new HashMap<>();
        labels.put(SESSION_ID_LABEL, "sessionId");
        labels.put("app.kubernetes.io/name", "nameLabel");
        ObjectMeta meta = new ObjectMeta(null, "", 0L, "", null, "generatedName", 1L, labels, null, "maasName", "namespace", null, "0", "", "uid");
        maas.setMetadata(meta);

        DeclarativeRequest declarativeRequest = maaSReconciler.declarativeRequestBuilder(maas);
        assertEquals(declarativeRequest.getMetadata().get("microserviceName"), "nameLabel");

        //2. test old label
        maas.setSpec(new RawExtension(Map.of("test-key", "test-value")));
        maas.setSubKind("TopicTemplate");
        labels = new HashMap<>();
        labels.put(SESSION_ID_LABEL, "sessionId");
        labels.put("app.kubernetes.io/instance", "nameLabel");
        ObjectMeta oldMeta = new ObjectMeta(null, "", 0L, "", null, "generatedName", 1L, labels, null, "maasName", "namespace", null, "0", "", "uid");
        maas.setMetadata(oldMeta);

        declarativeRequest = maaSReconciler.declarativeRequestBuilder(maas);
        assertEquals(declarativeRequest.getMetadata().get("microserviceName"), "nameLabel");
    }

    @Test
    void reconcileTest() throws Exception {
        Maas maas = new Maas();
        maas.setSpec(new RawExtension(Map.of("test-key", "test-value")));
        maas.setSubKind("TopicTemplate");
        Map<String, String> labels = new HashMap<>();
        labels.put(SESSION_ID_LABEL, "sessionId");
        ObjectMeta meta = new ObjectMeta(null, "", 0L, "", null, "generatedName", 1L, labels, null, "maasName", "namespace", null, "0", "", "uid");
        maas.setMetadata(meta);

        //1.
        UpdateControl<Maas> updateControl = maaSReconciler.reconcile(maas, null);

        assertNotNull(MDC.get(X_REQUEST_ID));
        assertEquals(UPDATING, updateControl.getResource().getStatus().getPhase());
        assertEquals(UPDATING.getValue(), MDC.get(PHASE));
        assertEquals(MDC.get(X_REQUEST_ID), updateControl.getResource().getStatus().getRequestId());
        assertEquals(1000L, updateControl.getScheduleDelay().get());

        //2.
        MDC.clear();
        when(maasDeclarativeClient.apply(eq("1"), any())).thenReturn(Response.ok().build());

        updateControl = maaSReconciler.reconcile(updateControl.getResource(), null);

        assertNotNull(MDC.get(X_REQUEST_ID));
        assertEquals(UPDATED_PHASE, updateControl.getResource().getStatus().getPhase());
        assertEquals(UPDATED_PHASE.getValue(), MDC.get(PHASE));
        assertEquals(MDC.get(X_REQUEST_ID), updateControl.getResource().getStatus().getRequestId());
        assertEquals(1000L, updateControl.getScheduleDelay().get());

        //3.
        MDC.clear();

        DeclarativeResponse resp = new DeclarativeResponse();
        resp.setStatus(ProcessStatus.IN_PROGRESS);
        resp.setTrackingId("test-tracking-id");
        List<Condition> conditions = new ArrayList<>();
        Condition condition = new Condition("conditionType", ProcessStatus.IN_PROGRESS, "reason", "message");
        conditions.add(condition);
        resp.setConditions(conditions);

        maas.getStatus().setTrackingId("test-tracking-id");

        when(maasDeclarativeClient.getStatus("1", "test-tracking-id")).thenReturn(Response.status(Response.Status.OK).entity(resp).build());
        maas.getStatus().setPhase(WAITING_FOR_DEPENDS);

        updateControl = maaSReconciler.reconcile(updateControl.getResource(), null);

        assertNotNull(MDC.get(X_REQUEST_ID));
        assertEquals(WAITING_FOR_DEPENDS, updateControl.getResource().getStatus().getPhase());
        assertEquals(WAITING_FOR_DEPENDS.getValue(), MDC.get(PHASE));
        assertEquals(MDC.get(X_REQUEST_ID), updateControl.getResource().getStatus().getRequestId());
        assertEquals(2000L, updateControl.getScheduleDelay().get());

        //4.
        MDC.clear();
        when(maasDeclarativeClient.apply(eq("1"), any())).thenReturn(Response.ok().build());
        maas.getStatus().setPhase(INVALID_CONFIGURATION);

        updateControl = maaSReconciler.reconcile(maas, null);

        assertNotNull(MDC.get(X_REQUEST_ID));
        assertTrue(updateControl.getScheduleDelay().isEmpty());

        //5.
        MDC.clear();
        when(maasDeclarativeClient.apply(eq("1"), any())).thenReturn(Response.ok().build());
        maas.getStatus().setPhase(UPDATED_PHASE);

        updateControl = maaSReconciler.reconcile(maas, null);

        assertNotNull(MDC.get(X_REQUEST_ID));
        assertTrue(updateControl.getScheduleDelay().isEmpty());
    }

    @Test
    void reconcileGenerationTest() throws Exception {
        Maas maas = new Maas();
        maas.setSpec(new RawExtension(Map.of("test-key", "test-value")));
        maas.setSubKind("TopicTemplate");
        Map<String, String> labels = new HashMap<>();
        labels.put(SESSION_ID_LABEL, "sessionId");
        ObjectMeta meta = new ObjectMeta(null, "", 0L, "", null, "generatedName", 1L, labels, null, "maasName", "namespace", null, "0", "", "uid");
        maas.setMetadata(meta);

        UpdateControl<Maas> updateControl = maaSReconciler.reconcile(maas, null);

        assertNotNull(MDC.get(X_REQUEST_ID));
        assertEquals(UPDATING, updateControl.getResource().getStatus().getPhase());
        assertEquals(UPDATING.getValue(), MDC.get(PHASE));
        assertEquals(MDC.get(X_REQUEST_ID), updateControl.getResource().getStatus().getRequestId());
        assertEquals(1000L, updateControl.getScheduleDelay().get());

        MDC.clear();
        when(maasDeclarativeClient.apply(eq("1"), any())).thenReturn(Response.ok().build());

        updateControl = maaSReconciler.reconcile(updateControl.getResource(), null);

        assertNotNull(MDC.get(X_REQUEST_ID));
        assertEquals(UPDATED_PHASE, updateControl.getResource().getStatus().getPhase());
        assertEquals(UPDATED_PHASE.getValue(), MDC.get(PHASE));
        assertEquals(MDC.get(X_REQUEST_ID), updateControl.getResource().getStatus().getRequestId());
        assertEquals(1000L, updateControl.getScheduleDelay().get());

        maas = updateControl.getResource();
        maas.getStatus().setObservedGeneration(2L);
        maas.setSpec(new RawExtension(Map.of("test-key1", "test-value1")));
        updateControl = maaSReconciler.reconcile(updateControl.getResource(), null);
        assertEquals(UPDATING, updateControl.getResource().getStatus().getPhase());
        assertEquals(UPDATING.getValue(), MDC.get(PHASE));
        assertEquals(Map.of("test-key1", "test-value1"), updateControl.getResource().getSpec().getValue());
    }

    @Test
    void firstTimeUpdatedPhaseTriggersUpdating() throws Exception {
        KubernetesClient kubernetesClient = mock(KubernetesClient.class);
        MaaSReconciler freshReconciler = new MaaSReconciler(kubernetesClient, maasDeclarativeClient);

        Maas maas = new Maas();
        maas.setSubKind("TopicTemplate");
        maas.setSpec(new RawExtension(Map.of("test-key", "test-value")));
        Map<String, String> labels = new HashMap<>();
        labels.put(SESSION_ID_LABEL, "sessionId");
        ObjectMeta meta = new ObjectMeta(null, "", 0L, "", null, "generatedName", 0L, labels, null, "maasName", "namespace", null, "0", "", "uid");
        maas.setMetadata(meta);
        maas.getStatus().setPhase(UPDATED_PHASE);
        maas.getStatus().getConditions().put("SomeCondition", new CoreCondition("1", "2", "m", "r", COMPLETED, true, "t"));

        UpdateControl<Maas> updateControl = freshReconciler.reconcile(maas, null);

        assertEquals(UPDATING, updateControl.getResource().getStatus().getPhase());
        assertTrue(updateControl.getResource().getStatus().getConditions().isEmpty());
        assertEquals(1000L, (long) updateControl.getScheduleDelay().get());
    }

    @Test
    void addOrUpdateConditionTest() throws Exception {
        //1. Add new Condition to empty list of conditions
        HashMap<String, CoreCondition> conditions = new HashMap<>();
        Condition condition = new Condition("ConditionType", COMPLETED, "reson", "message");
        maaSReconciler.addOrUpdateCondition(conditions, condition);
        assertEquals(1, conditions.size());
        assertEquals(conditions.get("ConditionType").getMessage(), condition.message());
        assertEquals(conditions.get("ConditionType").getReason(), condition.reason());
        assertEquals(conditions.get("ConditionType").getState(), condition.state());
        assertEquals(true, conditions.get("ConditionType").getStatus());
        assertEquals(conditions.get("ConditionType").getType(), condition.type());
        //2. Add new condition when similar condition is present
        maaSReconciler.addOrUpdateCondition(conditions, condition);
        assertEquals(1, conditions.size());
        //
        conditions.clear();
        //2. Add multiple conditions with partially filled fields
        Condition partialCondition = new Condition("ConditionType", COMPLETED, null, null);
        maaSReconciler.addOrUpdateCondition(conditions, condition);
        maaSReconciler.addOrUpdateCondition(conditions, partialCondition);
        maaSReconciler.addOrUpdateCondition(conditions, partialCondition);
        assertEquals(1, conditions.size());
        //
        conditions.clear();
        //3. Test condition is replaced, date updated
        maaSReconciler.addOrUpdateCondition(conditions, condition);
        String lastUpdateTime = conditions.get("ConditionType").getLastUpdateTime();
        await().pollDelay(Duration.ofSeconds(2)).until(() -> true);
        maaSReconciler.addOrUpdateCondition(conditions, condition);
        assertNotEquals(lastUpdateTime, conditions.get("ConditionType").getLastUpdateTime());
    }

    @Test
    void isResourceValidTest() throws Exception {
        //1. Invalid Resource, spec present, missing name and subKind
        Maas maas = new Maas();
        maas.setSpec(new RawExtension(Map.of("test-key", "test-value")));
        maaSReconciler.reconcile(maas, null);
        assertEquals(INVALID_CONFIGURATION, maas.getStatus().getPhase());
        CoreCondition condition = maas.getStatus().getConditions().get(VALIDATED_STEP_NAME);
        assertFalse(condition.getStatus());
        assertEquals(VALIDATED_STEP_NAME, condition.getType());
        assertEquals(ProcessStatus.FAILED, condition.getState());
        //2. Invalid Resource, spec present, name and subKind present but empty strings
        maas.setSpec(new RawExtension(Map.of("test-key", "test-value")));
        maas.setSubKind("");
        ObjectMeta meta = new ObjectMeta(null, "", 0L, "", null, "generatedName", 0L, null, null, "", "namespace", null, "0", "", "uid");
        maas.setMetadata(meta);
        maaSReconciler.reconcile(maas, null);
        assertEquals(INVALID_CONFIGURATION, maas.getStatus().getPhase());
        condition = maas.getStatus().getConditions().get(VALIDATED_STEP_NAME);
        assertFalse(condition.getStatus());
        assertEquals(VALIDATED_STEP_NAME, condition.getType());
        assertEquals(ProcessStatus.FAILED, condition.getState());
        //2. Invalid Resource, name and subKind present, missing spec
        maas.setSpec(null);
        maas.setSubKind("TopicTemplate");
        meta = new ObjectMeta(null, "", 0L, "", null, "generatedName", 0L, null, null, "maasName", "namespace", null, "0", "", "uid");
        maas.setMetadata(meta);
        maaSReconciler.reconcile(maas, null);
        assertEquals(INVALID_CONFIGURATION, maas.getStatus().getPhase());
        condition = maas.getStatus().getConditions().get(VALIDATED_STEP_NAME);
        assertFalse(condition.getStatus());
        assertEquals(VALIDATED_STEP_NAME, condition.getType());
        assertEquals(ProcessStatus.FAILED, condition.getState());
    }

    @Test
    void setUpLogFormatTest() throws Exception {
        Maas maas = new Maas();
        maas.getStatus().setRequestId("requestId");
        maas.setSubKind("TopicTemplate");
        Map<String, String> labels = new HashMap<>();
        labels.put(SESSION_ID_LABEL, "sessionId");
        ObjectMeta meta = new ObjectMeta(null, "", 0L, "", null, "generatedName", 0L, labels, null, "maasName", "namespace", null, "0", "", "uid");
        maas.setMetadata(meta);
        maaSReconciler.reconcile(maas, null);
        assertEquals("requestId", MDC.get(X_REQUEST_ID));
        assertEquals("sessionId", MDC.get(SESSION_ID_KEY));
        assertEquals("maasName", MDC.get(RESOURCE_NAME));
        assertEquals("MaaS", MDC.get(KIND));
        assertEquals("TopicTemplate", MDC.get(SUB_KIND));
        assertEquals(UNKNOWN.getValue(), MDC.get(PHASE));
    }

    @Test
    void buildConditionTmfErrorTest() throws Exception {
        doNothing().when(maaSReconciler).fireEvent(any(), any(), any());

        Maas maas = new Maas();
        maas.setSubKind("TopicTemplate");
        maas.setSpec(new RawExtension(Map.of("test-key", "test-value")));
        Map<String, String> labels = new HashMap<>();
        labels.put(SESSION_ID_LABEL, "sessionId");
        ObjectMeta meta = new ObjectMeta(null, "", 0L, "", null, "generatedName", 0L, labels, null, "maasName", "namespace", null, "0", "", "uid");
        maas.setMetadata(meta);

        //1. 500
        List<TmfError> errors = new ArrayList<>();
        TmfError error1 = new TmfError("error1", "referenceError1", "error1Code", "error1 reason", "error1 detail", "error1 status", null, new HashMap<>());
        errors.add(error1);
        TmfErrorResponse tmfErrorResponse = new TmfErrorResponse("errorId", "referenceError", "errorCode", "reason", "detail", "500", null, new HashMap<>(), errors, "type", "schemaLocation");
        maaSReconciler.buildCondition(maas, tmfErrorResponse);
        Mockito.verify(maaSReconciler, Mockito.times(1)).fireEvent(any(), any(), any());
        assertEquals(1, maas.getStatus().getConditions().size());
        assertEquals(BACKING_OFF.getValue(), MDC.get("phase"));

        Mockito.clearInvocations(maaSReconciler);
        errors.clear();
        MDC.clear();
        //2. 400
        error1 = new TmfError("error1", "referenceError1", "error1Code", "error1 reason", "error1 detail", "error1 status", null, new HashMap<>());
        errors.add(error1);
        tmfErrorResponse = new TmfErrorResponse("errorId", "referenceError", "errorCode", "reason", "detail", "400", null, new HashMap<>(), errors, "type", "schemaLocation");
        maaSReconciler.buildCondition(maas, tmfErrorResponse);
        Mockito.verify(maaSReconciler, Mockito.times(1)).fireEvent(any(), any(), any());
        assertEquals(1, maas.getStatus().getConditions().size());
        assertEquals(INVALID_CONFIGURATION.getValue(), MDC.get("phase"));
        //
        Mockito.clearInvocations(maaSReconciler);
        errors.clear();
        MDC.clear();
        //3. No Status
        error1 = new TmfError("error1", "referenceError1", "error1Code", "error1 reason", "error1 detail", "error1 status", null, new HashMap<>());
        errors.add(error1);
        tmfErrorResponse = new TmfErrorResponse("errorId", "referenceError", "errorCode", "reason", "detail", null, null, new HashMap<>(), errors, "type", "schemaLocation");
        maaSReconciler.buildCondition(maas, tmfErrorResponse);
        Mockito.verify(maaSReconciler, Mockito.times(1)).fireEvent(any(), any(), any());
        assertEquals(1, maas.getStatus().getConditions().size());
        assertEquals(BACKING_OFF.getValue(), MDC.get("phase"));
        //
        Mockito.clearInvocations(maaSReconciler);
        errors.clear();
        MDC.clear();
        //4. garbage Status
        error1 = new TmfError("error1", "referenceError1", "error1Code", "error1 reason", "error1 detail", "error1 status", null, new HashMap<>());
        errors.add(error1);
        tmfErrorResponse = new TmfErrorResponse("errorId", "referenceError", "errorCode", "reason", "detail", "garbage", null, new HashMap<>(), errors, "type", "schemaLocation");
        maaSReconciler.buildCondition(maas, tmfErrorResponse);
        Mockito.verify(maaSReconciler, Mockito.times(1)).fireEvent(any(), any(), any());
        assertEquals(1, maas.getStatus().getConditions().size());
        assertEquals(BACKING_OFF.getValue(), MDC.get("phase"));
    }

    @Test
    void buildConditionMultipleErrors() throws Exception {
        doNothing().when(maaSReconciler).fireEvent(any(), any(), any());

        Maas maas = new Maas();
        maas.setSubKind("TopicTemplate");
        maas.setSpec(new RawExtension(Map.of("test-key", "test-value")));
        Map<String, String> labels = new HashMap<>();
        labels.put(SESSION_ID_LABEL, "sessionId");
        ObjectMeta meta = new ObjectMeta(null, "", 0L, "", null, "generatedName", 0L, labels, null, "maasName", "namespace", null, "0", "", "uid");
        maas.setMetadata(meta);

        //same error type
        TmfErrorResponse tmfErrorResponse1 = new TmfErrorResponse("errorId", "referenceError", "errorCode", "reason", "detail", "500", null, new HashMap<>(), Collections.emptyList(), "type", "schemaLocation");
        maaSReconciler.buildCondition(maas, tmfErrorResponse1);
        TmfErrorResponse tmfErrorResponse2 = new TmfErrorResponse("errorId1", "referenceError1", "errorCode1", "reason1", "detail1", "501", null, new HashMap<>(), Collections.emptyList(), "type", "schemaLocation");
        maaSReconciler.buildCondition(maas, tmfErrorResponse1);
        assertEquals(1, maas.getStatus().getConditions().size());
        assertNotNull(maas.getStatus().getConditions().get(TYPE_UNKNOWN));

        //different error types
        tmfErrorResponse1 = new TmfErrorResponse("errorId", "referenceError", "errorCode", "reason", "detail", "500", null, new HashMap<>(), Collections.emptyList(), "type1", "schemaLocation");
        maaSReconciler.buildCondition(maas, tmfErrorResponse1);
        Map<String, Object> errorMeta = new HashMap<>();
        errorMeta.put("type", "type1");
        tmfErrorResponse2 = new TmfErrorResponse("errorId1", "referenceError1", "errorCode1", "reason1", "detail1", "501", null, errorMeta, Collections.emptyList(), "type2", "schemaLocation");
        maaSReconciler.buildCondition(maas, tmfErrorResponse2);
        assertEquals(2, maas.getStatus().getConditions().size());
        assertNotNull(maas.getStatus().getConditions().get(TYPE_UNKNOWN));
        assertNotNull(maas.getStatus().getConditions().get("type1"));
    }

    @Test
    void buildConditionDeclarativeResponseTest() throws Exception {
        doNothing().when(maaSReconciler).fireEvent(any(), any(), any());

        Maas maas = new Maas();
        maas.setSubKind("TopicTemplate");
        maas.setSpec(new RawExtension(Map.of("test-key", "test-value")));
        Map<String, String> labels = new HashMap<>();
        labels.put(SESSION_ID_LABEL, "sessionId");
        ObjectMeta meta = new ObjectMeta(null, "", 0L, "", null, "generatedName", 0L, labels, null, "maasName", "namespace", null, "0", "", "uid");
        maas.setMetadata(meta);

        DeclarativeResponse response = new DeclarativeResponse();
        response.setStatus(ProcessStatus.IN_PROGRESS);
        response.setTrackingId("trackingId");
        Condition partialCondition = new Condition("ConditionType", COMPLETED, "reason", "message");
        List<Condition> conditions = new ArrayList<>();
        conditions.add(partialCondition);
        response.setConditions(conditions);
        maaSReconciler.buildCondition(maas, response);

        assertEquals(response.getTrackingId(), maas.getStatus().getTrackingId());
        assertEquals(1, maas.getStatus().getConditions().size());

        CoreCondition condition = maas.getStatus().getConditions().get("ConditionType");
        assertEquals(COMPLETED, condition.getState());
        assertEquals("reason", condition.getReason());
        assertEquals("message", condition.getMessage());
        assertEquals("ConditionType", condition.getType());
    }

    @Test
    void buildConditionUnknown() throws Exception {
        Maas maas = new Maas();
        maas.setSubKind("TopicTemplate");
        maas.setSpec(new RawExtension(Map.of("test-key", "test-value")));

        assertThrows(ServerErrorException.class, () -> maaSReconciler.buildCondition(maas, "RandomObject"));
    }

    @Test
    void errorHandlerTest() throws Exception {
        Maas maas = new Maas();
        maas.setSubKind("TopicTemplate");
        maas.setSpec(new RawExtension(Map.of("test-key", "test-value")));
        Map<String, String> labels = new HashMap<>();
        labels.put(SESSION_ID_LABEL, "sessionId");
        ObjectMeta meta = new ObjectMeta(null, "", 0L, "", null, "generatedName", 0L, labels, null, "maasName", "namespace", null, "0", "", "uid");
        maas.setMetadata(meta);

        KubernetesClient kubernetesClient = mock(KubernetesClient.class);
        NamespaceableResource namespaceableResource = mock(NamespaceableResource.class);
        when(namespaceableResource.updateStatus()).thenReturn(mock(CoreResource.class));
        when(kubernetesClient.resource(any(HasMetadata.class))).thenReturn(namespaceableResource);
        MaaSReconciler rec = new MaaSReconciler(kubernetesClient, maasDeclarativeClient);

        //1.
        IllegalStateException e = new IllegalStateException("Some exception");
        rec.errorHandler(e, maas);
        CoreCondition condition = maas.getStatus().getConditions().get(TYPE_UNKNOWN);
        assertEquals(MESSAGE_UNKNOWN, condition.getMessage());
        assertEquals(ProcessStatus.FAILED, condition.getState());
        assertEquals("Some exception", condition.getReason());
        //
        maas.getStatus().getConditions().clear();
        //2.
        MaaSReconciler reconciler = spy(rec);
        doNothing().when(reconciler).fireEvent(any(), any(), any());
        TmfErrorResponse tmfErrorResponse = new TmfErrorResponse("errorId", "referenceError", "errorCode", "reason", "detail", "500", null, new HashMap<>(), Collections.emptyList(), "type", "schemaLocation");
        WebApplicationException ex = new WebApplicationException(Response.status(500).entity(tmfErrorResponse).build());
        reconciler.errorHandler(ex, maas);
        condition = maas.getStatus().getConditions().get(TYPE_UNKNOWN);
        assertEquals("detail", condition.getMessage());
        assertEquals("errorCode:reason", condition.getReason());
        assertEquals(TYPE_UNKNOWN, condition.getType());
        assertEquals(ProcessStatus.FAILED, condition.getState());
    }
}
