package com.netcracker.core.declarative.service.composite;

import com.netcracker.cloud.quarkus.consul.client.model.GetValue;
import com.netcracker.core.declarative.service.composite.consul.CompositeStructureUpdateEvent;
import com.netcracker.core.declarative.service.composite.consul.CompositeStructureRefUpdateEvent;
import com.netcracker.core.declarative.service.composite.consul.longpoll.ConsulLongPoller;
import com.netcracker.core.declarative.service.composite.consul.longpoll.ConsulUpdateEventFactory;
import com.netcracker.core.declarative.service.composite.consul.longpoll.LongPollSession;
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
    private LongPollSession refLongPollSession;
    private LongPollSession structureLongPollSession;

    private CompositeStructureRefChangeListener watcher;

    @BeforeEach
    void setUp() {
        consulLongPoller = mock(ConsulLongPoller.class);
        refLongPollSession = createMockWatchHandle();
        structureLongPollSession = createMockWatchHandle();

        when(consulLongPoller.startWatch(eq(STRUCTURE_REF_KEY), any()))
                .thenReturn(refLongPollSession);

        watcher = new CompositeStructureRefChangeListener(NAMESPACE, consulLongPoller);
    }

    @Test
    void startShouldCreateAndStartRefWatch() {
        watcher.start();

        verify(consulLongPoller).startWatch(eq(STRUCTURE_REF_KEY), any());
    }

    @Test
    void startShouldBeIdempotent() {
        watcher.start();
        watcher.start();

        verify(consulLongPoller, times(1)).startWatch(eq(STRUCTURE_REF_KEY), any());
    }

    @Test
    void stopShouldCancelRefWatch() {
        watcher.start();
        watcher.stop();

        verify(refLongPollSession).cancel();
    }

    @Test
    void newPrefixShouldStartStructureWatch() {
        when(consulLongPoller.startWatch(eq("prefix/one"), any()))
                .thenReturn(structureLongPollSession);

        watcher.start();
        simulateRefSnapshot("prefix/one");

        verify(consulLongPoller).startWatch(eq("prefix/one"), any());
    }

    @Test
    void prefixChangeShouldSwitchWatch() {
        LongPollSession newStructureLongPollSession = createMockWatchHandle();
        when(consulLongPoller.startWatch(eq("prefix/one"), any()))
                .thenReturn(structureLongPollSession);
        when(consulLongPoller.startWatch(eq("prefix/two"), any()))
                .thenReturn(newStructureLongPollSession);

        watcher.start();
        simulateRefSnapshot("prefix/one");
        simulateRefSnapshot("prefix/two");

        verify(structureLongPollSession).cancel();
        verify(consulLongPoller).startWatch(eq("prefix/two"), any());
    }

    @Test
    void samePrefixShouldNotRestartWatch() {
        when(consulLongPoller.startWatch(eq("prefix/one"), any()))
                .thenReturn(structureLongPollSession);

        watcher.start();
        simulateRefSnapshot("prefix/one");
        simulateRefSnapshot("prefix/one");

        verify(consulLongPoller, times(1)).startWatch(eq("prefix/one"), any());
        verify(structureLongPollSession, never()).cancel();
    }

    @Test
    void blankPrefixShouldCancelWatch() {
        when(consulLongPoller.startWatch(eq("prefix/one"), any()))
                .thenReturn(structureLongPollSession);

        watcher.start();
        simulateRefSnapshot("prefix/one");
        simulateRefSnapshot("   ");

        verify(structureLongPollSession).cancel();
    }

    @Test
    void stopShouldCancelAllWatches() {
        when(consulLongPoller.startWatch(eq("prefix/one"), any()))
                .thenReturn(structureLongPollSession);

        watcher.start();
        simulateRefSnapshot("prefix/one");
        watcher.stop();

        verify(refLongPollSession).cancel();
        verify(structureLongPollSession).cancel();
    }

    @Test
    void eventAfterStopShouldBeIgnored() {
        watcher.start();
        watcher.stop();
        simulateRefSnapshot("prefix/one");

        verify(consulLongPoller, never()).startWatch(eq("prefix/one"), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldUseCorrectEventFactoryForStructureWatch() {
        ArgumentCaptor<ConsulUpdateEventFactory<CompositeStructureUpdateEvent>> factoryCaptor =
                ArgumentCaptor.forClass(ConsulUpdateEventFactory.class);
        when(consulLongPoller.startWatch(eq("prefix/one"), factoryCaptor.capture()))
                .thenReturn(structureLongPollSession);

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
        watcher.onCompositeStructureRefUpdated(event);
    }

    /**
     * Creates a mock WatchHandle that tracks cancelled state properly.
     */
    private static LongPollSession createMockWatchHandle() {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        LongPollSession handle = mock(LongPollSession.class);
        when(handle.isCancelled()).thenAnswer(inv -> cancelled.get());
        org.mockito.Mockito.doAnswer(inv -> {
            cancelled.set(true);
            return null;
        }).when(handle).cancel();
        return handle;
    }
}
