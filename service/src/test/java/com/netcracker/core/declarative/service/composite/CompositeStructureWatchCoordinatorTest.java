package com.netcracker.core.declarative.service.composite;

import com.netcracker.core.declarative.client.k8s.ConfigMapClient;
import com.netcracker.core.declarative.service.composite.consul.ConsulClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompositeStructureWatchCoordinatorTest {

    private static final String NAMESPACE = "test-ns";

    private ConfigMapClient configMapClient;
    private CompositeStructureWatcher watcher;
    private CompositeStructureWatchCoordinator coordinator;

    @BeforeEach
    void setUp() {
        configMapClient = mock(ConfigMapClient.class);
        watcher = mock(CompositeStructureWatcher.class);
        ConsulClient consulClient = mock(ConsulClient.class);
        CompositeStructureSnapshotHandler handler = mock(CompositeStructureSnapshotHandler.class);

        coordinator = new CompositeStructureWatchCoordinator(NAMESPACE, consulClient, handler, configMapClient);
        // Replace the watcher with our mock for testing
        try {
            java.lang.reflect.Field watcherField = CompositeStructureWatchCoordinator.class.getDeclaredField("compositeStructureWatcher");
            watcherField.setAccessible(true);
            watcherField.set(coordinator, watcher);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject mock watcher", e);
        }
    }

    @Test
    void ensureWatcherStateShouldStartWatcherWhenManagedByCoreOperator() {
        when(configMapClient.shouldBeManagedByCoreOperator("composite-structure", NAMESPACE)).thenReturn(true);

        coordinator.ensureWatcherState();

        verify(configMapClient).shouldBeManagedByCoreOperator("composite-structure", NAMESPACE);
        verify(watcher).start();
    }

    @Test
    void ensureWatcherStateShouldNotStartWatcherWhenNotManagedByCoreOperator() {
        when(configMapClient.shouldBeManagedByCoreOperator("composite-structure", NAMESPACE)).thenReturn(false);

        coordinator.ensureWatcherState();

        verify(configMapClient).shouldBeManagedByCoreOperator("composite-structure", NAMESPACE);
        verify(watcher, never()).start();
    }

    @Test
    void ensureWatcherStateShouldStopWatcherWhenOwnershipChanges() {
        when(configMapClient.shouldBeManagedByCoreOperator("composite-structure", NAMESPACE))
                .thenReturn(true)
                .thenReturn(false);

        coordinator.ensureWatcherState();
        coordinator.ensureWatcherState();

        verify(watcher).start();
        verify(watcher).stop();
    }

    @Test
    void ensureWatcherStateShouldNotFailOnConfigMapClientException() {
        when(configMapClient.shouldBeManagedByCoreOperator("composite-structure", NAMESPACE))
                .thenThrow(new RuntimeException("API error"));

        coordinator.ensureWatcherState();

        verify(watcher, never()).start();
        verify(watcher, never()).stop();
    }

    @Test
    void stopShouldStopWatcher() {
        when(configMapClient.shouldBeManagedByCoreOperator("composite-structure", NAMESPACE)).thenReturn(true);

        coordinator.ensureWatcherState();
        coordinator.stop();

        verify(watcher).stop();
    }
}
