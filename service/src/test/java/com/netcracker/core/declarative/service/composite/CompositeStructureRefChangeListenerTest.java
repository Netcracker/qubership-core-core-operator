package com.netcracker.core.declarative.service.composite;

import com.netcracker.cloud.quarkus.consul.client.model.GetValue;
import com.netcracker.core.declarative.service.composite.consul.CompositeStructureUpdateEvent;
import com.netcracker.core.declarative.service.composite.consul.CompositeStructureRefUpdateEvent;
import com.netcracker.core.declarative.service.composite.consul.longpoll.ConsulLongPoller;
import com.netcracker.core.declarative.service.composite.consul.longpoll.ConsulUpdateEventFactory;
import com.netcracker.core.declarative.service.composite.consul.longpoll.LongPoolSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompositeStructureRefChangeListenerTest {

    private static final String NAMESPACE = "ns";
    private static final String STRUCTURE_REF_KEY = "config/ns/application/composite/structureRef";

    private ConsulLongPoller consulLongPoller;
    private LongPoolSession refLongPoolSession;
    private LongPoolSession structureLongPoolSession;

    private CompositeStructureRefChangeListener watcher;

    @BeforeEach
    void setUp() {
        consulLongPoller = mock(ConsulLongPoller.class);
        refLongPoolSession = createMockWatchHandle();
        structureLongPoolSession = createMockWatchHandle();

        when(consulLongPoller.startWatchConsulRoot(eq(STRUCTURE_REF_KEY), any()))
                .thenReturn(refLongPoolSession);

        watcher = new CompositeStructureRefChangeListener(NAMESPACE, consulLongPoller);
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

        verify(refLongPoolSession).cancel();
    }

    @Test
    void newPrefixShouldStartStructureWatch() {
        when(consulLongPoller.startWatchConsulRoot(eq("prefix/one"), any()))
                .thenReturn(structureLongPoolSession);

        watcher.start();
        simulateRefSnapshot("prefix/one");

        verify(consulLongPoller).startWatchConsulRoot(eq("prefix/one"), any());
    }

    @Test
    void prefixChangeShouldSwitchWatch() {
        LongPoolSession newStructureLongPoolSession = createMockWatchHandle();
        when(consulLongPoller.startWatchConsulRoot(eq("prefix/one"), any()))
                .thenReturn(structureLongPoolSession);
        when(consulLongPoller.startWatchConsulRoot(eq("prefix/two"), any()))
                .thenReturn(newStructureLongPoolSession);

        watcher.start();
        simulateRefSnapshot("prefix/one");
        simulateRefSnapshot("prefix/two");

        verify(structureLongPoolSession).cancel();
        verify(consulLongPoller).startWatchConsulRoot(eq("prefix/two"), any());
    }

    @Test
    void samePrefixShouldNotRestartWatch() {
        when(consulLongPoller.startWatchConsulRoot(eq("prefix/one"), any()))
                .thenReturn(structureLongPoolSession);

        watcher.start();
        simulateRefSnapshot("prefix/one");
        simulateRefSnapshot("prefix/one");

        verify(consulLongPoller, times(1)).startWatchConsulRoot(eq("prefix/one"), any());
        verify(structureLongPoolSession, never()).cancel();
    }

    @Test
    void blankPrefixShouldCancelWatch() {
        when(consulLongPoller.startWatchConsulRoot(eq("prefix/one"), any()))
                .thenReturn(structureLongPoolSession);

        watcher.start();
        simulateRefSnapshot("prefix/one");
        simulateRefSnapshot("   ");

        verify(structureLongPoolSession).cancel();
    }

    @Test
    void stopShouldCancelAllWatches() {
        when(consulLongPoller.startWatchConsulRoot(eq("prefix/one"), any()))
                .thenReturn(structureLongPoolSession);

        watcher.start();
        simulateRefSnapshot("prefix/one");
        watcher.stop();

        verify(refLongPoolSession).cancel();
        verify(structureLongPoolSession).cancel();
    }

    @Test
    void eventAfterStopShouldBeIgnored() {
        watcher.start();
        watcher.stop();
        simulateRefSnapshot("prefix/one");

        verify(consulLongPoller, never()).startWatchConsulRoot(eq("prefix/one"), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldUseCorrectEventFactoryForStructureWatch() {
        ArgumentCaptor<ConsulUpdateEventFactory<CompositeStructureUpdateEvent>> factoryCaptor =
                ArgumentCaptor.forClass(ConsulUpdateEventFactory.class);
        when(consulLongPoller.startWatchConsulRoot(eq("prefix/one"), factoryCaptor.capture()))
                .thenReturn(structureLongPoolSession);

        watcher.start();
        simulateRefSnapshot("prefix/one");

        ConsulUpdateEventFactory<CompositeStructureUpdateEvent> factory = factoryCaptor.getValue();
        GetValue mockValue = mock(GetValue.class);
        CompositeStructureUpdateEvent event = factory.create(List.of(mockValue), 123L);

        assertThat(event).isInstanceOf(CompositeStructureUpdateEvent.class);
        assertThat(event.getValues()).containsExactly(mockValue);
        assertThat(event.getConsulIndex()).isEqualTo(123L);
    }

    private void simulateRefSnapshot(String prefix) {
        GetValue getValue = mock(GetValue.class);
        when(getValue.getKey()).thenReturn(STRUCTURE_REF_KEY);
        when(getValue.getDecodedValue()).thenReturn(prefix);

        CompositeStructureRefUpdateEvent event = new CompositeStructureRefUpdateEvent(
                prefix == null || prefix.isBlank() ? Collections.emptyList() : List.of(getValue),
                100L
        );
        watcher.onCompositeStructureRefSnapshot(event);
    }

    /**
     * Creates a mock WatchHandle that tracks cancelled state properly.
     */
    private static LongPoolSession createMockWatchHandle() {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        LongPoolSession handle = mock(LongPoolSession.class);
        when(handle.isCancelled()).thenAnswer(inv -> cancelled.get());
        org.mockito.Mockito.doAnswer(inv -> {
            cancelled.set(true);
            return null;
        }).when(handle).cancel();
        return handle;
    }
}
