package com.netcracker.core.declarative.client.reconciler;

import com.netcracker.core.declarative.client.rest.CompositeClient;
import com.netcracker.core.declarative.model.CompositeMembersList;
import com.netcracker.core.declarative.resources.base.CoreCondition;
import com.netcracker.core.declarative.resources.base.CoreResource;
import com.netcracker.core.declarative.resources.composite.Composite;
import com.netcracker.core.declarative.service.CompositeConsulUpdater;
import com.netcracker.core.declarative.service.CompositeSpec;
import com.netcracker.core.declarative.service.CompositeStructureUpdateNotifier;
import com.netcracker.core.declarative.service.NoopCompositeConsulUpdaterImpl;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.runtime.RawExtension;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.NamespaceableResource;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.netcracker.core.declarative.client.reconciler.CompositeReconciler.DBAAS_NAME;
import static com.netcracker.core.declarative.client.reconciler.CompositeReconciler.MAAS_NAME;
import static com.netcracker.core.declarative.client.reconciler.CompositeReconciler.XAAS_UPDATED_STEP_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CompositeReconcilerTest {

    @Test
    void reconcileInternal() throws Exception {
        CompositeClient compositeClient = mock(CompositeClient.class);
        when(compositeClient.structures(any())).thenReturn(Response.noContent().build());

        CompositeConsulUpdater compositeConsulUpdater = mock(CompositeConsulUpdater.class);
        when(compositeConsulUpdater.getCompositeMembers(any())).thenReturn(new CompositeMembersList(123L, Set.of()));

        CompositeReconciler compositeReconciler = new CompositeReconciler(
                mock(KubernetesClient.class),
                compositeConsulUpdater,
                List.of(
                        new CompositeStructureUpdateNotifier(MAAS_NAME, compositeClient),
                        new CompositeStructureUpdateNotifier(DBAAS_NAME, compositeClient)
                )
        );

        Composite composite = new Composite();
        composite.setSpec(new RawExtension(new CompositeSpec("C", "O", "P", new CompositeSpec.CompositeSpecBaseline("BC", "BS", "BP"))));
        compositeReconciler.reconcileInternal(composite);

        assertNotNull(findConditionByType(composite, "Validated"));
        assertTrue(findConditionByType(composite, "Validated").getStatus());

        assertNotNull(findConditionByType(composite, "CompositeStructureUpdated"));
        assertTrue(findConditionByType(composite, "CompositeStructureUpdated").getStatus());

        assertNotNull(findConditionByType(composite, XAAS_UPDATED_STEP_NAME.apply(MAAS_NAME)));
        assertTrue(findConditionByType(composite, XAAS_UPDATED_STEP_NAME.apply(MAAS_NAME)).getStatus());

        assertNotNull(findConditionByType(composite, XAAS_UPDATED_STEP_NAME.apply(DBAAS_NAME)));
        assertTrue(findConditionByType(composite, XAAS_UPDATED_STEP_NAME.apply(DBAAS_NAME)).getStatus());
    }

    @Test
    void reconcileInternal_modify_index() throws Exception {
        CompositeClient compositeClient = mock(CompositeClient.class);
        when(compositeClient.structures(any())).thenReturn(Response.noContent().build());

        CompositeConsulUpdater compositeConsulUpdater = mock(CompositeConsulUpdater.class);
        when(compositeConsulUpdater.getCompositeMembers(any())).thenReturn(new CompositeMembersList(123L, Set.of("ns-1", "ns-2")));

        CompositeReconciler compositeReconciler = new CompositeReconciler(
                mock(KubernetesClient.class),
                compositeConsulUpdater,
                List.of(
                        new CompositeStructureUpdateNotifier(MAAS_NAME, compositeClient),
                        new CompositeStructureUpdateNotifier(DBAAS_NAME, compositeClient)
                )
        );

        Composite composite = new Composite();
        composite.setSpec(new RawExtension(new CompositeSpec("", "ns-1", "", null)));
        compositeReconciler.reconcileInternal(composite);

        verify(compositeClient, times(2)).structures(new CompositeClient.Request("ns-1", Set.of("ns-1", "ns-2"), 123L));

    }

        @Test
    void reconcileInternal_no_consul() throws Exception {
        KubernetesClient kubernetesClient = mock(KubernetesClient.class);
        NamespaceableResource namespaceableResource = mock(NamespaceableResource.class);
        when(namespaceableResource.updateStatus()).thenReturn(mock(CoreResource.class));
        when(kubernetesClient.resource(any(HasMetadata.class))).thenReturn(namespaceableResource);
        CompositeClient compositeClient = mock(CompositeClient.class);
        when(compositeClient.structures(any())).thenReturn(Response.noContent().build());
        CompositeReconciler compositeReconciler = new CompositeReconciler(
                kubernetesClient,
                new NoopCompositeConsulUpdaterImpl(),
                List.of(
                        new CompositeStructureUpdateNotifier(MAAS_NAME, compositeClient),
                        new CompositeStructureUpdateNotifier(DBAAS_NAME, compositeClient)
                )
        );

        Composite composite = new Composite();
        composite.setSpec(new RawExtension(new CompositeSpec("C", "O", "P", new CompositeSpec.CompositeSpecBaseline("BC", "BS", "BP"))));
        compositeReconciler.reconcileInternal(composite);

        assertTrue(composite.getStatus().isUpdated());

        assertNotNull(findConditionByType(composite, "Validated"));
        assertTrue(findConditionByType(composite, "Validated").getStatus());

        CoreCondition compositeStructureUpdated = findConditionByType(composite, "CompositeStructureUpdated");
        assertNotNull(compositeStructureUpdated);
        assertFalse(compositeStructureUpdated.getStatus());
        assertEquals("consul disabled", compositeStructureUpdated.getMessage());
        assertEquals("Consul integration is disabled; skip composite CR processing", compositeStructureUpdated.getReason());
    }

    @Test
    void reconcileInternal_fail_Validated() throws Exception {
        KubernetesClient kubernetesClient = mock(KubernetesClient.class);
        NamespaceableResource namespaceableResource = mock(NamespaceableResource.class);
        when(namespaceableResource.updateStatus()).thenReturn(mock(CoreResource.class));
        when(kubernetesClient.resource(any(HasMetadata.class))).thenReturn(namespaceableResource);
        CompositeReconciler compositeReconciler = new CompositeReconciler(
                kubernetesClient,
                mock(CompositeConsulUpdater.class),
                List.of()
        );

        Composite composite = new Composite();
        composite.setSpec(new RawExtension(new CompositeSpec("C", null, "P", new CompositeSpec.CompositeSpecBaseline("BC", "BS", "BP"))));
        compositeReconciler.reconcileInternal(composite);

        CoreCondition validated = findConditionByType(composite, "Validated");
        assertNotNull(validated);
        assertFalse(validated.getStatus());
        assertTrue(validated.getReason().contains("Origin namespace cannot be null or empty"));
    }

    @Test
    void reconcileInternal_fail_CompositeStructureUpdated() throws Exception {
        KubernetesClient kubernetesClient = mock(KubernetesClient.class);
        NamespaceableResource namespaceableResource = mock(NamespaceableResource.class);
        when(namespaceableResource.updateStatus()).thenReturn(mock(CoreResource.class));
        when(kubernetesClient.resource(any(HasMetadata.class))).thenReturn(namespaceableResource);

        CompositeConsulUpdater compositeConsulUpdater = mock(CompositeConsulUpdater.class);
        doThrow(new RuntimeException("test-exception")).when(compositeConsulUpdater).updateCompositeStructureInConsul(any());
        CompositeReconciler compositeReconciler = new CompositeReconciler(
                kubernetesClient,
                compositeConsulUpdater,
                List.of()
        );

        Composite composite = new Composite();
        composite.setSpec(new RawExtension(new CompositeSpec("C", "O", "P", new CompositeSpec.CompositeSpecBaseline("BC", "BS", "BP"))));
        compositeReconciler.reconcileInternal(composite);

        assertNotNull(findConditionByType(composite, "Validated"));
        assertTrue(findConditionByType(composite, "Validated").getStatus());

        CoreCondition compositeStructureUpdated = findConditionByType(composite, "CompositeStructureUpdated");
        assertNotNull(compositeStructureUpdated);
        assertFalse(compositeStructureUpdated.getStatus());
        assertEquals("test-exception", compositeStructureUpdated.getReason());
    }

    @Test
    void reconcileInternal_fail_MaaSUpdate() throws Exception {
        CompositeClient compositeClient = mock(CompositeClient.class);
        when(compositeClient.structures(any())).thenThrow(new RuntimeException("test-exception"));

        KubernetesClient kubernetesClient = mock(KubernetesClient.class);
        NamespaceableResource namespaceableResource = mock(NamespaceableResource.class);
        when(namespaceableResource.updateStatus()).thenReturn(mock(CoreResource.class));
        when(kubernetesClient.resource(any(HasMetadata.class))).thenReturn(namespaceableResource);

        CompositeConsulUpdater compositeConsulUpdater = mock(CompositeConsulUpdater.class);
        when(compositeConsulUpdater.getCompositeMembers(any())).thenReturn(new CompositeMembersList(123L, Set.of()));

        CompositeReconciler compositeReconciler = new CompositeReconciler(
                kubernetesClient,
                compositeConsulUpdater,
                List.of(new CompositeStructureUpdateNotifier(MAAS_NAME, compositeClient))
        );

        Composite composite = new Composite();
        composite.setSpec(new RawExtension(new CompositeSpec("C", "O", "P", new CompositeSpec.CompositeSpecBaseline("BC", "BS", "BP"))));
        compositeReconciler.reconcileInternal(composite);

        assertNotNull(findConditionByType(composite, "Validated"));
        assertTrue(findConditionByType(composite, "Validated").getStatus());

        assertNotNull(findConditionByType(composite, "CompositeStructureUpdated"));
        assertTrue(findConditionByType(composite, "CompositeStructureUpdated").getStatus());

        CoreCondition maaSUpdated = findConditionByType(composite, "MaaSUpdated");
        assertNotNull(maaSUpdated);
        assertFalse(maaSUpdated.getStatus());
        assertEquals("test-exception", maaSUpdated.getReason());
    }

    @Test
    void reconcileInternal_MaaSUpdate_fail_response() throws Exception {
        CompositeClient compositeClient = mock(CompositeClient.class);
        when(compositeClient.structures(any()))
                .thenReturn(Response
                        .status(500)
                        .entity("test error")
                        .build()
                );

        KubernetesClient kubernetesClient = mock(KubernetesClient.class);
        NamespaceableResource namespaceableResource = mock(NamespaceableResource.class);
        when(namespaceableResource.updateStatus()).thenReturn(mock(CoreResource.class));
        when(kubernetesClient.resource(any(HasMetadata.class))).thenReturn(namespaceableResource);

        CompositeConsulUpdater compositeConsulUpdater = mock(CompositeConsulUpdater.class);
        when(compositeConsulUpdater.getCompositeMembers(any())).thenReturn(new CompositeMembersList(123L, Set.of()));

        CompositeReconciler compositeReconciler = new CompositeReconciler(
                kubernetesClient,
                compositeConsulUpdater,
                List.of(new CompositeStructureUpdateNotifier(MAAS_NAME, compositeClient))
        );

        Composite composite = new Composite();
        composite.setSpec(new RawExtension(new CompositeSpec("C", "O", "P", new CompositeSpec.CompositeSpecBaseline("BC", "BS", "BP"))));
        compositeReconciler.reconcileInternal(composite);

        CoreCondition maaSUpdated = findConditionByType(composite, "MaaSUpdated");
        assertNotNull(maaSUpdated);
        assertFalse(maaSUpdated.getStatus());
        assertEquals("Unexpected response received from XaaS: 500, test error", maaSUpdated.getReason());
    }

    @Test
    void reconcileInternal_MaaSUpdate_fail_tmf_response() throws Exception {
        CompositeClient compositeClient = mock(CompositeClient.class);
        when(compositeClient.structures(any()))
                .thenReturn(Response
                        .serverError()
                        .entity("""
                                    {
                                      "id": "47f79f65-82a0-4401-8321-d31abb3bd07d",
                                      "status": "500",
                                      "code": "MAAS-0600",
                                      "message": "test message",
                                      "reason": "test reason",
                                      "@type": "NC.TMFErrorResponse.v1.0"
                                    }
                                """)
                        .build()
                );

        KubernetesClient kubernetesClient = mock(KubernetesClient.class);
        NamespaceableResource namespaceableResource = mock(NamespaceableResource.class);
        when(namespaceableResource.updateStatus()).thenReturn(mock(CoreResource.class));
        when(kubernetesClient.resource(any(HasMetadata.class))).thenReturn(namespaceableResource);

        CompositeConsulUpdater compositeConsulUpdater = mock(CompositeConsulUpdater.class);
        when(compositeConsulUpdater.getCompositeMembers(any())).thenReturn(new CompositeMembersList(123L, Set.of()));

        CompositeReconciler compositeReconciler = new CompositeReconciler(
                kubernetesClient,
                compositeConsulUpdater,
                List.of(new CompositeStructureUpdateNotifier(MAAS_NAME, compositeClient))
        );

        Composite composite = new Composite();
        composite.setSpec(new RawExtension(new CompositeSpec("C", "O", "P", new CompositeSpec.CompositeSpecBaseline("BC", "BS", "BP"))));
        compositeReconciler.reconcileInternal(composite);

        CoreCondition maaSUpdated = findConditionByType(composite, "MaaSUpdated");
        assertNotNull(maaSUpdated);
        assertFalse(maaSUpdated.getStatus());
        assertEquals("[MAAS-0600][47f79f65-82a0-4401-8321-d31abb3bd07d] test message", maaSUpdated.getReason());
    }

    @Test
    void fromResource() {
        Composite c = new Composite();
        c.setSpec(new RawExtension(Map.of(
                "controllerNamespace", "C",
                "originNamespace", "O",
                "peerNamespace", "P",
                "baseline", Map.of(
                        "controllerNamespace", "BC",
                        "originNamespace", "BO",
                        "peerNamespace", "BP"
                )
        )));
        assertEquals(new CompositeSpec("C", "O", "P", new CompositeSpec.CompositeSpecBaseline("BC", "BO", "BP")), CompositeReconciler.fromResource(c));
    }

    private CoreCondition findConditionByType(Composite composite, String type) {
        return composite.getStatus().getConditions().get(type);
    }
}