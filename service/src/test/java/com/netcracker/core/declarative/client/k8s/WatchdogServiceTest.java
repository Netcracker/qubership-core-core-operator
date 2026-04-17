package com.netcracker.core.declarative.client.k8s;

import com.netcracker.core.declarative.resources.mesh.Mesh;
import com.netcracker.core.declarative.resources.mesh.MeshList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.RegisteredController;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WatchdogServiceTest {

    @Mock
    Operator operator;

    @Mock
    KubernetesClient kubernetesClient;

    @Mock
    RegisteredController registeredController;

    @Mock
    ControllerConfiguration controllerConfiguration;

    @InjectMocks
    WatchdogService watchdogService;

    @BeforeEach
    void setUp() throws Exception {
        setField("namespace", "core-1-core");
        setField("watchdogEnabled", true);
        setField("confirmationSeconds", 60L);
        setField("probeTimeoutSeconds", 10L);
        setField("divergenceDetectedAt", null);
        setField("reconnecting", new AtomicBoolean(false));
        setField("lastReconcilerResourceVersion", new AtomicReference<>(null));
        setField("lastObservedResourceVersion", new AtomicReference<>(null));
    }

    @Test
    void recordActivity_updatesResourceVersion() throws Exception {
        setField("divergenceDetectedAt", Instant.now());

        watchdogService.recordActivity("42000");

        assertEquals("42000", getAtomicString("lastReconcilerResourceVersion").get());
        assertNull(getField("divergenceDetectedAt"));
    }

    @Test
    void recordActivity_ignoresNullResourceVersion() throws Exception {
        setField("lastReconcilerResourceVersion", new AtomicReference<>("42000"));

        watchdogService.recordActivity(null);

        assertEquals("42000", getAtomicString("lastReconcilerResourceVersion").get());
    }

    @Test
    void isNewerVersion_returnsTrue_whenClusterVersionHigher() {
        assertTrue(watchdogService.isNewerVersion("42100", "42000"));
    }

    @Test
    void isNewerVersion_returnsFalse_whenVersionsEqual() {
        assertFalse(watchdogService.isNewerVersion("42000", "42000"));
    }

    @Test
    void isNewerVersion_returnsFalse_whenClusterVersionLower() {
        assertFalse(watchdogService.isNewerVersion("41000", "42000"));
    }

    @Test
    void isNewerVersion_returnsFalse_whenVersionsNotParseable() {
        assertFalse(watchdogService.isNewerVersion("abc", "def"));
    }

    @Test
    void checkWatchHealth_doesNothing_whenDisabled() throws Exception {
        setField("watchdogEnabled", false);

        watchdogService.checkWatchHealth();

        verifyNoInteractions(kubernetesClient, operator);
    }

    @Test
    void checkWatchHealth_doesNothing_whenReconnectInProgress() throws Exception {
        setField("reconnecting", new AtomicBoolean(true));

        watchdogService.checkWatchHealth();

        verifyNoInteractions(kubernetesClient, operator);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void checkWatchHealth_doesNothing_whenApiserverUnreachable() throws Exception {
        setField("probeTimeoutSeconds", 1L);

        MixedOperation op = mock(MixedOperation.class);
        when(op.inNamespace("core-1-core")).thenReturn(op);
        when(op.list()).thenAnswer(inv -> { Thread.sleep(3_000); return null; });
        when(kubernetesClient.resources(Mesh.class)).thenReturn(op);

        watchdogService.checkWatchHealth();

        verify(operator, never()).getRegisteredControllers();
    }

    @Test
    void checkWatchHealth_doesNothing_whenNoReconcilerActivityYet() throws Exception {
        setupClusterRV("42000");

        watchdogService.checkWatchHealth();

        verify(operator, never()).getRegisteredControllers();
    }

    @Test
    void checkWatchHealth_doesNothing_whenClusterVersionNotChanged() throws Exception {
        setField("lastReconcilerResourceVersion", new AtomicReference<>("42000"));
        setupClusterRV("42000");

        watchdogService.checkWatchHealth();

        verify(operator, never()).getRegisteredControllers();
        assertNull(getField("divergenceDetectedAt"));
    }

    @Test
    void checkWatchHealth_doesNothing_whenClusterVersionLower() throws Exception {
        setField("lastReconcilerResourceVersion", new AtomicReference<>("42000"));
        setupClusterRV("41000");
        watchdogService.checkWatchHealth();

        verify(operator, never()).getRegisteredControllers();
    }

    @Test
    void checkWatchHealth_setsDivergenceTime_onFirstDetection() throws Exception {
        setField("lastReconcilerResourceVersion", new AtomicReference<>("42000"));
        setupClusterRV("42100");

        watchdogService.checkWatchHealth();

        assertNotNull(getField("divergenceDetectedAt"));
        verify(operator, never()).getRegisteredControllers();
    }

    @Test
    void checkWatchHealth_doesNotReconnect_beforeConfirmationElapsed() throws Exception {
        setField("lastReconcilerResourceVersion", new AtomicReference<>("42000"));
        setField("divergenceDetectedAt", Instant.now().minusSeconds(10));
        setupClusterRV("42100");

        watchdogService.checkWatchHealth();

        verify(operator, never()).getRegisteredControllers();
    }

    @Test
    void checkWatchHealth_reconnects_afterConfirmationElapsed() throws Exception {
        setField("lastReconcilerResourceVersion", new AtomicReference<>("42000"));
        setField("divergenceDetectedAt", Instant.now().minusSeconds(90));
        setupClusterRV("42100");
        setupControllers();

        watchdogService.checkWatchHealth();

        Set<String> expectedNs = Set.of("core-1-core");
        verify(registeredController).changeNamespaces(expectedNs);
    }

    @Test
    void checkWatchHealth_resetsDivergence_whenVersionCatchesUp() throws Exception {
        setField("lastReconcilerResourceVersion", new AtomicReference<>("42100"));
        setField("divergenceDetectedAt", Instant.now().minusSeconds(30));
        setupClusterRV("42100"); 

        watchdogService.checkWatchHealth();

        assertNull(getField("divergenceDetectedAt"));
        verify(operator, never()).getRegisteredControllers();
    }

    @Test
    void reconnect_reconnectsAllControllers() throws Exception {
        setField("lastReconcilerResourceVersion", new AtomicReference<>("42000"));
        setField("divergenceDetectedAt", Instant.now().minusSeconds(90));
        setupClusterRV("42100");

        RegisteredController rc2 = mock(RegisteredController.class);
        ControllerConfiguration cfg2 = mock(ControllerConfiguration.class);
        when(rc2.getConfiguration()).thenReturn(cfg2);
        when(cfg2.getName()).thenReturn("DBaaSReconciler");
        when(registeredController.getConfiguration()).thenReturn(controllerConfiguration);
        when(controllerConfiguration.getName()).thenReturn("MeshReconciler");
        when(operator.getRegisteredControllers()).thenReturn(Set.of(registeredController, rc2));

        watchdogService.checkWatchHealth();

        Set<String> expectedNs = Set.of("core-1-core");
        verify(registeredController).changeNamespaces(expectedNs);
        verify(rc2).changeNamespaces(expectedNs);
    }

    @Test
    void reconnect_resetsDivergenceDetectedAt_afterSuccess() throws Exception {
        setField("lastReconcilerResourceVersion", new AtomicReference<>("42000"));
        setField("divergenceDetectedAt", Instant.now().minusSeconds(90));
        setupClusterRV("42100");
        setupControllers();

        watchdogService.checkWatchHealth();

        assertNull(getField("divergenceDetectedAt"));
    }

    @Test
    void reconnect_resetsReconnectingFlag_evenOnException() throws Exception {
        setField("lastReconcilerResourceVersion", new AtomicReference<>("42000"));
        setField("divergenceDetectedAt", Instant.now().minusSeconds(90));
        setupClusterRV("42100");
        when(operator.getRegisteredControllers())
            .thenThrow(new RuntimeException("unexpected"));

        watchdogService.checkWatchHealth();

        assertFalse(((AtomicBoolean) getField("reconnecting")).get());
    }

    @Test
    void reconnect_continuesWithOtherControllers_whenOneControllerFails() throws Exception {
        setField("lastReconcilerResourceVersion", new AtomicReference<>("42000"));
        setField("divergenceDetectedAt", Instant.now().minusSeconds(90));
        setupClusterRV("42100");

        RegisteredController rc2 = mock(RegisteredController.class);
        ControllerConfiguration cfg2 = mock(ControllerConfiguration.class);
        when(rc2.getConfiguration()).thenReturn(cfg2);
        when(cfg2.getName()).thenReturn("DBaaSReconciler");
        when(registeredController.getConfiguration()).thenReturn(controllerConfiguration);
        when(controllerConfiguration.getName()).thenReturn("MeshReconciler");
        doThrow(new RuntimeException("failed"))
            .when(registeredController)
            .changeNamespaces(ArgumentMatchers.<Set<String>>any());
        when(operator.getRegisteredControllers()).thenReturn(Set.of(registeredController, rc2));

        assertDoesNotThrow(() -> watchdogService.checkWatchHealth());

        verify(rc2).changeNamespaces(Set.of("core-1-core"));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void setupClusterRV(String rv) {
        io.fabric8.kubernetes.api.model.ListMeta meta =
            new io.fabric8.kubernetes.api.model.ListMeta();
        meta.setResourceVersion(rv);

        MeshList meshList = mock(MeshList.class);
        when(meshList.getMetadata()).thenReturn(meta);

        MixedOperation op = mock(MixedOperation.class);
        when(op.inNamespace("core-1-core")).thenReturn(op);
        when(op.list()).thenReturn(meshList);

        when(kubernetesClient.resources(Mesh.class)).thenReturn(op);
    }

    private void setupControllers() {
        when(operator.getRegisteredControllers()).thenReturn(Set.of(registeredController));
        when(registeredController.getConfiguration()).thenReturn(controllerConfiguration);
        when(controllerConfiguration.getName()).thenReturn("MeshReconciler");
    }

    @SuppressWarnings("unchecked")
    private <T> T getField(String name) throws Exception {
        Field f = findField(name);
        f.setAccessible(true);
        return (T) f.get(watchdogService);
    }

    private AtomicReference<String> getAtomicString(String name) throws Exception {
        Field f = findField(name);
        f.setAccessible(true);
        return (AtomicReference<String>) f.get(watchdogService);
    }

    private void setField(String name, Object value) throws Exception {
        Field f = findField(name);
        f.setAccessible(true);
        f.set(watchdogService, value);
    }

    private Field findField(String name) {
        Class<?> clazz = watchdogService.getClass();
        while (clazz != null) {
            try {
                return clazz.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new RuntimeException("Field not found: " + name);
    }
}