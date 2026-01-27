package com.netcracker.core.declarative.service.composite;

import com.netcracker.cloud.quarkus.consul.client.model.GetValue;
import com.netcracker.core.declarative.service.composite.consul.longpoll2.CompositeStructureConsulUpdateEvent;
import com.netcracker.core.declarative.service.composite.consul.longpoll2.CompositeStructureRefConsulUpdateEvent;
import com.netcracker.core.declarative.service.composite.consul.longpoll2.ConsulLongPoller;
import com.netcracker.core.declarative.service.composite.consul.longpoll2.ConsulUpdateEventFactory;
import com.netcracker.core.declarative.service.composite.consul.longpoll2.WatchHandle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompositeStructureWatcherTest {

    private static final String NAMESPACE = "ns";
    private static final String STRUCTURE_REF_KEY = "config/ns/application/composite/structureRef";

    private ConsulLongPoller consulLongPoller;
    private WatchHandle refWatchHandle;
    private WatchHandle structureWatchHandle;

    private CompositeStructureWatcher watcher;

    @BeforeEach
    void setUp() {
        consulLongPoller = mock(ConsulLongPoller.class);
        refWatchHandle = mock(WatchHandle.class);
        structureWatchHandle = mock(WatchHandle.class);

        when(consulLongPoller.startWatchConsulRoot(eq(STRUCTURE_REF_KEY), any()))
                .thenReturn(refWatchHandle);

        watcher = new CompositeStructureWatcher(NAMESPACE, consulLongPoller);
    }

    @Test
    void startShouldCreateAndStartRefWatch() {
        watcher.start();

        verify(consulLongPoller).startWatchConsulRoot(eq(STRUCTURE_REF_KEY), any());
    }

    @Test
    void startShouldBeIdempotent() {
        watcher.start();
        watcher.start();

        verify(consulLongPoller, times(1)).startWatchConsulRoot(eq(STRUCTURE_REF_KEY), any());
    }

    @Test
    void stopShouldCancelRefWatch() {
        watcher.start();
        watcher.stop();

        verify(refWatchHandle).cancel();
    }

    @Test
    void newPrefixShouldStartStructureWatch() {
        when(consulLongPoller.startWatchConsulRoot(eq("prefix/one"), any()))
                .thenReturn(structureWatchHandle);

        watcher.start();
        simulateRefSnapshot("prefix/one");

        verify(consulLongPoller).startWatchConsulRoot(eq("prefix/one"), any());
    }

    @Test
    void prefixChangeShouldSwitchWatch() {
        WatchHandle newStructureWatchHandle = mock(WatchHandle.class);
        when(consulLongPoller.startWatchConsulRoot(eq("prefix/one"), any()))
                .thenReturn(structureWatchHandle);
        when(consulLongPoller.startWatchConsulRoot(eq("prefix/two"), any()))
                .thenReturn(newStructureWatchHandle);

        watcher.start();
        simulateRefSnapshot("prefix/one");
        simulateRefSnapshot("prefix/two");

        verify(structureWatchHandle).cancel();
        verify(consulLongPoller).startWatchConsulRoot(eq("prefix/two"), any());
    }

    @Test
    void samePrefixShouldNotRestartWatch() {
        when(consulLongPoller.startWatchConsulRoot(eq("prefix/one"), any()))
                .thenReturn(structureWatchHandle);

        watcher.start();
        simulateRefSnapshot("prefix/one");
        simulateRefSnapshot("prefix/one");

        verify(consulLongPoller, times(1)).startWatchConsulRoot(eq("prefix/one"), any());
        verify(structureWatchHandle, never()).cancel();
    }

    @Test
    void blankPrefixShouldCancelWatch() {
        when(consulLongPoller.startWatchConsulRoot(eq("prefix/one"), any()))
                .thenReturn(structureWatchHandle);

        watcher.start();
        simulateRefSnapshot("prefix/one");
        simulateRefSnapshot("   ");

        verify(structureWatchHandle).cancel();
    }

    @Test
    void stopShouldCancelAllWatches() {
        when(consulLongPoller.startWatchConsulRoot(eq("prefix/one"), any()))
                .thenReturn(structureWatchHandle);

        watcher.start();
        simulateRefSnapshot("prefix/one");
        watcher.stop();

        verify(refWatchHandle).cancel();
        verify(structureWatchHandle).cancel();
    }

    @Test
    void eventAfterStopShouldBeIgnored() {
        when(consulLongPoller.startWatchConsulRoot(eq("prefix/one"), any()))
                .thenReturn(structureWatchHandle);

        watcher.start();
        watcher.stop();
        simulateRefSnapshot("prefix/one");

        verify(consulLongPoller, never()).startWatchConsulRoot(eq("prefix/one"), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldUseCorrectEventFactoryForStructureWatch() {
        ArgumentCaptor<ConsulUpdateEventFactory<CompositeStructureConsulUpdateEvent>> factoryCaptor =
                ArgumentCaptor.forClass(ConsulUpdateEventFactory.class);
        when(consulLongPoller.startWatchConsulRoot(eq("prefix/one"), factoryCaptor.capture()))
                .thenReturn(structureWatchHandle);

        watcher.start();
        simulateRefSnapshot("prefix/one");

        ConsulUpdateEventFactory<CompositeStructureConsulUpdateEvent> factory = factoryCaptor.getValue();
        GetValue mockValue = mock(GetValue.class);
        CompositeStructureConsulUpdateEvent event = factory.create(List.of(mockValue), 123L);

        assertThat(event).isInstanceOf(CompositeStructureConsulUpdateEvent.class);
        assertThat(event.getValues()).containsExactly(mockValue);
        assertThat(event.getConsulIndex()).isEqualTo(123L);
    }

    private void simulateRefSnapshot(String prefix) {
        GetValue getValue = mock(GetValue.class);
        when(getValue.getKey()).thenReturn(STRUCTURE_REF_KEY);
        when(getValue.getValue()).thenReturn(prefix);

        CompositeStructureRefConsulUpdateEvent event = new CompositeStructureRefConsulUpdateEvent(
                prefix == null || prefix.isBlank() ? Collections.emptyList() : List.of(getValue),
                100L
        );
        watcher.onCompositeStructureRefSnapshot(event);
    }
}
