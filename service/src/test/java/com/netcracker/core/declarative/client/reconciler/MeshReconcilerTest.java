package com.netcracker.core.declarative.client.reconciler;

import com.netcracker.core.declarative.client.rest.DeclarativeRequest;
import com.netcracker.core.declarative.client.rest.deprecated.MeshClientV3;
import com.netcracker.core.declarative.resources.base.CoreResource;
import com.netcracker.core.declarative.resources.base.Phase;
import com.netcracker.core.declarative.resources.mesh.Mesh;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.runtime.RawExtension;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.NamespaceableResource;
import jakarta.ws.rs.ServerErrorException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MeshReconcilerTest {
    MeshClientV3 meshClientV3 = mock(MeshClientV3.class);

    @Test
    void declarativeRequestBuilderTest() {
        MeshReconciler c = getReconciler();

        Mesh mesh = new Mesh();
        mesh.setKind("Mesh");
        mesh.setSubKind("RouteConfig");
        Map<String, Object> testMap = Map.of("test-key", "test-value");
        mesh.setSpec(new RawExtension(testMap));

        DeclarativeRequest result = c.declarativeRequestBuilder(mesh);
        assertTrue(result.getSpec() instanceof Map<?,?>);
        assertEquals("test-value", ((Map<?, ?>) result.getSpec()).get("test-key"));

        mesh.setSubKind("RoutesDrop");
        List<Map<String, Object>> testList = Collections.singletonList(testMap);
        mesh.setSpec(new RawExtension(Map.of("entities", testList)));

        result = c.declarativeRequestBuilder(mesh);
        assertTrue(result.getSpec() instanceof List<?>);
        assertEquals(testList.size(), ((List<?>) result.getSpec()).size());
        assertEquals(testMap, ((List<?>) result.getSpec()).get(0));
    }

    @Test
    void reconcileInternalSuccessTest() throws Exception {
        when(meshClientV3.applyConfig(any())).thenReturn(Response.ok().build());
        MeshReconciler c = getReconciler();

        Mesh mesh = new Mesh();
        mesh.setKind("Mesh");
        mesh.setSubKind("RouteConfig");
        mesh.setSpec(new RawExtension(Map.of("test-key", "test-value")));
        Map<String, String> labels = new HashMap<>();
        ObjectMeta meta = new ObjectMeta(null, "", 0L, "", null, "generatedName", 0L, labels, null, "maasName", "namespace", null, "0", "", "uid");
        mesh.setMetadata(meta);

        c.reconcileInternal(mesh);
        assertEquals(Phase.UPDATED_PHASE, mesh.getStatus().getPhase());
    }

    @Test
    void reconcileInternalErrorTest() throws Exception {
        when(meshClientV3.applyConfig(any())).thenReturn(Response.serverError().build());
        MeshReconciler c = getReconciler();

        Mesh mesh = new Mesh();
        mesh.setKind("Mesh");
        mesh.setSubKind("RouteConfig");
        mesh.setSpec(new RawExtension(Map.of("test-key", "test-value")));
        Map<String, String> labels = new HashMap<>();
        ObjectMeta meta = new ObjectMeta(null, "", 0L, "", null, "generatedName", 0L, labels, null, "maasName", "namespace", null, "0", "", "uid");
        mesh.setMetadata(meta);

        assertThrows(ServerErrorException.class, () -> c.reconcileInternal(mesh));
    }

    private MeshReconciler getReconciler() {
        KubernetesClient kubernetesClient = mock(KubernetesClient.class);
        NamespaceableResource namespaceableResource = mock(NamespaceableResource.class);
        when(namespaceableResource.updateStatus()).thenReturn(mock(CoreResource.class));
        when(kubernetesClient.resource(any(HasMetadata.class))).thenReturn(namespaceableResource);
        MeshReconciler meshReconciler = new MeshReconciler(kubernetesClient, meshClientV3);
        MeshReconciler c = spy(meshReconciler);
        doNothing().when(c).fireEvent(any(), any(), any());

        return c;
    }
}
