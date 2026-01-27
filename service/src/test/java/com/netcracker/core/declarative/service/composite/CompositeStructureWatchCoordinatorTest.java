package com.netcracker.core.declarative.service.composite;

import com.netcracker.core.declarative.client.k8s.ConfigMapClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompositeStructureWatchCoordinatorTest {

    private static final String NAMESPACE = "test-ns";

    private ConfigMapClient configMapClient;
    private CompositeStructureRefChangeListener watcher;
    private CompositeStructureWatchCoordinator coordinator;

    @BeforeEach
    void setUp() {
        configMapClient = mock(ConfigMapClient.class);
        watcher = mock(CompositeStructureRefChangeListener.class);

        coordinator = new CompositeStructureWatchCoordinator(NAMESPACE, watcher, configMapClient);
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
