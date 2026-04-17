package com.netcracker.core.declarative.client.k8s;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.VersionInfo;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.RegisteredController;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

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
        setField(watchdogService, "namespace", "test-namespace");
        setField(watchdogService, "silenceDetectedAt", null);
        setField(watchdogService, "reconnecting", new AtomicBoolean(false));
        setField(watchdogService, "lastReconcileTime",
            new AtomicReference<>(Instant.now()));
    }

    // --- recordActivity ---

    @Test
    void recordActivity_updatesLastReconcileTime() throws Exception {
        Instant before = Instant.now().minusSeconds(200);
        setField(watchdogService, "lastReconcileTime", new AtomicReference<>(before));
        setField(watchdogService, "silenceDetectedAt", Instant.now());

        watchdogService.recordActivity();

        AtomicReference<Instant> lastTime = getField(watchdogService, "lastReconcileTime");
        assertTrue(lastTime.get().isAfter(before));
        assertNull(getField(watchdogService, "silenceDetectedAt"));
    }

    // --- checkWatchHealth: sunnyday scenario ---

    @Test
    void checkWatchHealth_doesNothing_whenReconcilerActiveRecently() {
        // lastReconcileTime = now → silence = 0 sec < 120 sec threshold
        watchdogService.checkWatchHealth();

        verifyNoInteractions(kubernetesClient, operator);
    }

    @Test
    void checkWatchHealth_doesNothing_whenReconnectInProgress() throws Exception {
        setField(watchdogService, "reconnecting", new AtomicBoolean(true));
        setField(watchdogService, "lastReconcileTime",
            new AtomicReference<>(Instant.now().minusSeconds(300)));

        watchdogService.checkWatchHealth();

        verifyNoInteractions(kubernetesClient, operator);
    }

    // --- checkWatchHealth: apiserver is not available ---

    @Test
    void checkWatchHealth_doesNotReconnect_whenApiserverUnreachable() throws Exception {
        // Silence > threshold → start probing apiserver
        setField(watchdogService, "lastReconcileTime",
            new AtomicReference<>(Instant.now().minusSeconds(200)));
        // Apiserver does not respond — probe hangs and times out
        when(kubernetesClient.getKubernetesVersion())
            .thenAnswer(inv -> { Thread.sleep(15_000); return null; });

        watchdogService.checkWatchHealth();

        verify(operator, never()).getRegisteredControllers();
    }

    // --- checkWatchHealth: silence detection ---

    @Test
    void checkWatchHealth_setsDetectionTime_onFirstSilenceDetection() throws Exception {
        setField(watchdogService, "lastReconcileTime",
            new AtomicReference<>(Instant.now().minusSeconds(200)));
        when(kubernetesClient.getKubernetesVersion()).thenReturn(mock(VersionInfo.class));

        watchdogService.checkWatchHealth();

        assertNotNull(getField(watchdogService, "silenceDetectedAt"));
        verify(operator, never()).getRegisteredControllers();
    }

    // --- checkWatchHealth: acceptance and reconnect ---

    @Test
    void checkWatchHealth_doesNotReconnect_beforeConfirmationPeriod() throws Exception {
        setField(watchdogService, "lastReconcileTime",
            new AtomicReference<>(Instant.now().minusSeconds(200)));
        // silenceDetectedAt = 10 sec ago < 30 sec confirmation
        setField(watchdogService, "silenceDetectedAt",
            Instant.now().minusSeconds(10));
        when(kubernetesClient.getKubernetesVersion()).thenReturn(mock(VersionInfo.class));

        watchdogService.checkWatchHealth();

        verify(operator, never()).getRegisteredControllers();
    }

    @Test
    void checkWatchHealth_reconnects_afterConfirmationPeriod() throws Exception {
        setField(watchdogService, "lastReconcileTime",
            new AtomicReference<>(Instant.now().minusSeconds(300)));
        // silenceDetectedAt = 40 sec ago > 30 sec confirmation → reconnect
        setField(watchdogService, "silenceDetectedAt",
            Instant.now().minusSeconds(40));
        when(kubernetesClient.getKubernetesVersion()).thenReturn(mock(VersionInfo.class));

        when(operator.getRegisteredControllers()).thenReturn(Set.of(registeredController));
        when(registeredController.getConfiguration()).thenReturn(controllerConfiguration);
        when(controllerConfiguration.getName()).thenReturn("MeshReconciler");

        watchdogService.checkWatchHealth();

        verify(registeredController).changeNamespaces(Set.of("test-namespace"));

        assertNull(getField(watchdogService, "silenceDetectedAt"));
        assertFalse(((AtomicBoolean) getField(watchdogService, "reconnecting")).get());
    }

    @Test
    void checkWatchHealth_reconnectsAllControllers() throws Exception {
        setField(watchdogService, "lastReconcileTime",
            new AtomicReference<>(Instant.now().minusSeconds(300)));
        setField(watchdogService, "silenceDetectedAt",
            Instant.now().minusSeconds(40));
        when(kubernetesClient.getKubernetesVersion()).thenReturn(mock(VersionInfo.class));

        RegisteredController rc2 = mock(RegisteredController.class);
        ControllerConfiguration cfg2 = mock(ControllerConfiguration.class);
        when(rc2.getConfiguration()).thenReturn(cfg2);
        when(cfg2.getName()).thenReturn("DBaaSReconciler");

        when(operator.getRegisteredControllers()).thenReturn(Set.of(registeredController, rc2));
        when(registeredController.getConfiguration()).thenReturn(controllerConfiguration);
        when(controllerConfiguration.getName()).thenReturn("MeshReconciler");

        watchdogService.checkWatchHealth();

        verify(registeredController).changeNamespaces(Set.of("test-namespace"));
        verify(rc2).changeNamespaces(Set.of("test-namespace"));
    }

    @Test
    void checkWatchHealth_resetsReconnectingFlag_evenOnException() throws Exception {
        setField(watchdogService, "lastReconcileTime",
            new AtomicReference<>(Instant.now().minusSeconds(300)));
        setField(watchdogService, "silenceDetectedAt",
            Instant.now().minusSeconds(40));
        when(kubernetesClient.getKubernetesVersion()).thenReturn(mock(VersionInfo.class));
        when(operator.getRegisteredControllers())
            .thenThrow(new RuntimeException("unexpected"));

        watchdogService.checkWatchHealth();

        assertFalse(((AtomicBoolean) getField(watchdogService, "reconnecting")).get());
    }

    // --- helpers ---

    @SuppressWarnings("unchecked")
    private <T> T getField(Object obj, String name) throws Exception {
        Field f = findField(obj.getClass(), name);
        f.setAccessible(true);
        return (T) f.get(obj);
    }

    private void setField(Object obj, String name, Object value) throws Exception {
        Field f = findField(obj.getClass(), name);
        f.setAccessible(true);
        f.set(obj, value);
    }

    private Field findField(Class<?> clazz, String name) {
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