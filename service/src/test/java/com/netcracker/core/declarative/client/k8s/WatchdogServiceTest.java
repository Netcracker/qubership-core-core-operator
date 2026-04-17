package com.netcracker.core.declarative.client.k8s;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.VersionInfo;
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
        setField("namespace", "test-namespace");
        setField("watchdogEnabled", true);
        setField("silenceThresholdSeconds", 300L);
        setField("confirmationSeconds", 60L);
        setField("probeTimeoutSeconds", 10L);
        setField("silenceDetectedAt", null);
        setField("reconnecting", new AtomicBoolean(false));
        setField("lastReconcileTime", new AtomicReference<>(Instant.now()));
    }

    @Test
    void recordActivity_updatesLastReconcileTime() throws Exception {
        Instant oldTime = Instant.now().minusSeconds(200);
        setField("lastReconcileTime", new AtomicReference<>(oldTime));
        setField("silenceDetectedAt", Instant.now());

        watchdogService.recordActivity();

        Instant updated = getAtomicInstant("lastReconcileTime").get();
        assertTrue(updated.isAfter(oldTime));
        assertNull(getField("silenceDetectedAt"));
    }

    @Test
    void checkWatchHealth_doesNothing_whenDisabled() throws Exception {
        setField("watchdogEnabled", false);
        setField("lastReconcileTime",
            new AtomicReference<>(Instant.now().minusSeconds(999)));

        watchdogService.checkWatchHealth();

        verifyNoInteractions(kubernetesClient, operator);
    }

    @Test
    void checkWatchHealth_doesNothing_whenReconcilerActiveRecently() {
        watchdogService.checkWatchHealth();

        verifyNoInteractions(kubernetesClient, operator);
    }

    @Test
    void checkWatchHealth_doesNothing_whenReconnectAlreadyInProgress() throws Exception {
        setField("reconnecting", new AtomicBoolean(true));
        setField("lastReconcileTime",
            new AtomicReference<>(Instant.now().minusSeconds(999)));

        watchdogService.checkWatchHealth();

        verifyNoInteractions(kubernetesClient, operator);
    }

    @Test
    void checkWatchHealth_doesNotReconnect_whenApiserverTimesOut() throws Exception {
        setField("lastReconcileTime",
            new AtomicReference<>(Instant.now().minusSeconds(400)));
        setField("probeTimeoutSeconds", 1L);
        when(kubernetesClient.getKubernetesVersion())
            .thenAnswer(inv -> { Thread.sleep(3_000); return null; });

        watchdogService.checkWatchHealth();

        verify(operator, never()).getRegisteredControllers();
    }

    @Test
    void checkWatchHealth_doesNotReconnect_whenApiserverThrowsException() throws Exception {
        setField("lastReconcileTime",
            new AtomicReference<>(Instant.now().minusSeconds(400)));
        when(kubernetesClient.getKubernetesVersion())
            .thenThrow(new RuntimeException("connection refused"));

        watchdogService.checkWatchHealth();

        verify(operator, never()).getRegisteredControllers();
    }

    @Test
    void checkWatchHealth_setsDetectionTime_onFirstSilenceWithApiserverOk() throws Exception {
        setField("lastReconcileTime",
            new AtomicReference<>(Instant.now().minusSeconds(400)));
        when(kubernetesClient.getKubernetesVersion()).thenReturn(mock(VersionInfo.class));

        watchdogService.checkWatchHealth();

        assertNotNull(getField("silenceDetectedAt"));
        verify(operator, never()).getRegisteredControllers();
    }

    @Test
    void checkWatchHealth_doesNotReconnect_beforeConfirmationPeriodElapsed() throws Exception {
        setField("lastReconcileTime",
            new AtomicReference<>(Instant.now().minusSeconds(400)));
        setField("silenceDetectedAt", Instant.now().minusSeconds(10));
        when(kubernetesClient.getKubernetesVersion()).thenReturn(mock(VersionInfo.class));

        watchdogService.checkWatchHealth();

        verify(operator, never()).getRegisteredControllers();
    }

    @Test
    void checkWatchHealth_reconnects_afterConfirmationPeriodElapsed() throws Exception {
        setupSilentState();
        when(operator.getRegisteredControllers()).thenReturn(Set.of(registeredController));
        when(registeredController.getConfiguration()).thenReturn(controllerConfiguration);
        when(controllerConfiguration.getName()).thenReturn("MeshReconciler");

        watchdogService.checkWatchHealth();

        verify(registeredController).changeNamespaces(Set.<String>of("test-namespace"));
    }

    @Test
    void reconnect_reconnectsAllControllers() throws Exception {
        setupSilentState();
        RegisteredController rc2 = mock(RegisteredController.class);
        ControllerConfiguration cfg2 = mock(ControllerConfiguration.class);
        when(rc2.getConfiguration()).thenReturn(cfg2);
        when(cfg2.getName()).thenReturn("DBaaSReconciler");
        when(registeredController.getConfiguration()).thenReturn(controllerConfiguration);
        when(controllerConfiguration.getName()).thenReturn("MeshReconciler");
        when(operator.getRegisteredControllers()).thenReturn(Set.of(registeredController, rc2));

        watchdogService.checkWatchHealth();

        verify(registeredController).changeNamespaces(Set.<String>of("test-namespace"));
        verify(rc2).changeNamespaces(Set.<String>of("test-namespace"));
    }

    @Test
    void reconnect_resetsSilenceDetectedAt_afterSuccess() throws Exception {
        setupSilentState();
        when(operator.getRegisteredControllers()).thenReturn(Set.of(registeredController));
        when(registeredController.getConfiguration()).thenReturn(controllerConfiguration);
        when(controllerConfiguration.getName()).thenReturn("MeshReconciler");

        watchdogService.checkWatchHealth();

        assertNull(getField("silenceDetectedAt"));
    }

    @Test
    void reconnect_appliesCooldown_afterSuccess() throws Exception {
        setupSilentState();
        when(operator.getRegisteredControllers()).thenReturn(Set.of(registeredController));
        when(registeredController.getConfiguration()).thenReturn(controllerConfiguration);
        when(controllerConfiguration.getName()).thenReturn("MeshReconciler");

        watchdogService.checkWatchHealth();

        Instant lastTime = getAtomicInstant("lastReconcileTime").get();
        assertTrue(lastTime.isAfter(Instant.now()),
            "lastReconcileTime должен быть в будущем после cooldown");
    }

    @Test
    void reconnect_resetsReconnectingFlag_evenOnException() throws Exception {
        setupSilentState();
        when(operator.getRegisteredControllers())
            .thenThrow(new RuntimeException("unexpected error"));

        watchdogService.checkWatchHealth();

        assertFalse(((AtomicBoolean) getField("reconnecting")).get(),
            "reconnecting должен быть false даже после исключения");
    }

    @Test
    void reconnect_continuesWithOtherControllers_whenOneControllerFails() throws Exception {
        setupSilentState();
        RegisteredController rc2 = mock(RegisteredController.class);
        ControllerConfiguration cfg2 = mock(ControllerConfiguration.class);
        when(rc2.getConfiguration()).thenReturn(cfg2);
        when(cfg2.getName()).thenReturn("DBaaSReconciler");
        when(registeredController.getConfiguration()).thenReturn(controllerConfiguration);
        when(controllerConfiguration.getName()).thenReturn("MeshReconciler");
        doThrow(new RuntimeException("failed"))
            .when(registeredController).changeNamespaces(ArgumentMatchers.<Set<String>>any());
        when(operator.getRegisteredControllers()).thenReturn(Set.of(registeredController, rc2));

        assertDoesNotThrow(() -> watchdogService.checkWatchHealth());

        verify(rc2).changeNamespaces(Set.<String>of("test-namespace"));
    }

    private void setupSilentState() throws Exception {
        setField("lastReconcileTime",
            new AtomicReference<>(Instant.now().minusSeconds(400)));
        setField("silenceDetectedAt", Instant.now().minusSeconds(90));
        when(kubernetesClient.getKubernetesVersion()).thenReturn(mock(VersionInfo.class));
    }

    @SuppressWarnings("unchecked")
    private <T> T getField(String name) throws Exception {
        Field f = findField(name);
        f.setAccessible(true);
        return (T) f.get(watchdogService);
    }

    @SuppressWarnings("unchecked")
    private AtomicReference<Instant> getAtomicInstant(String name) throws Exception {
        Field f = findField(name);
        f.setAccessible(true);
        return (AtomicReference<Instant>) f.get(watchdogService);
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