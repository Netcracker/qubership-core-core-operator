package com.netcracker.core.declarative.service.composite;

import com.netcracker.cloud.quarkus.consul.client.model.GetValue;
import com.netcracker.core.declarative.client.k8s.ConfigMapClient;
import com.netcracker.core.declarative.service.composite.consul.CompositeStructureUpdateEvent;
import com.netcracker.core.declarative.service.composite.consul.longpoll.ConsulLongPoller;
import com.netcracker.core.declarative.service.composite.consul.longpoll.ConsulUpdateEventFactory;
import com.netcracker.core.declarative.service.composite.consul.longpoll.LongPollSession;
import io.vertx.mutiny.core.Vertx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CompositeStructureWatcherTest {

    private static final String NAMESPACE = "test-ns";
    private static final String COMPOSITE_ID = "test-composite";
    private static final String COMPOSITE_STRUCTURE_KEY = "composite/test-composite/structure";

    private ConsulLongPoller consulLongPoller;
    private ConfigMapClient configMapClient;
    private Vertx vertx;
    private LongPollSession longPollSession;
    private Consumer<Long> periodicHandler;

    private CompositeStructureWatcher watcher;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        consulLongPoller = mock(ConsulLongPoller.class);
        configMapClient = mock(ConfigMapClient.class);
        vertx = mock(Vertx.class);
        longPollSession = createMockLongPollSession();

        when(vertx.setPeriodic(anyLong(), any(Consumer.class))).thenAnswer(invocation -> {
            periodicHandler = invocation.getArgument(1);
            return 1L; // timer id
        });
        when(consulLongPoller.startWatch(eq(COMPOSITE_STRUCTURE_KEY), any())).thenReturn(longPollSession);

        watcher = new CompositeStructureWatcher(NAMESPACE, true, consulLongPoller, configMapClient, vertx);
    }

    // === Start lifecycle tests ===

    @Test
    void startShouldSchedulePeriodicTask() {
        watcher.start(COMPOSITE_ID);

        verify(vertx).setPeriodic(eq(300000L), any(Consumer.class));
    }

    @Test
    void startShouldBeIdempotent() {
        watcher.start(COMPOSITE_ID);
        watcher.start(COMPOSITE_ID);

        verify(vertx, times(1)).setPeriodic(anyLong(), any(Consumer.class));
    }

    @Test
    void startWithNullCompositeIdShouldNotStart() {
        watcher.start(null);

        verifyNoInteractions(vertx);
    }

    @Test
    void startWithBlankCompositeIdShouldNotStart() {
        watcher.start("   ");

        verifyNoInteractions(vertx);
    }

    // === Ownership-based long-poll control tests ===

    @Test
    void shouldStartLongPollWhenManagedByCoreOperator() {
        when(configMapClient.shouldBeManagedByCoreOperator("composite-structure", NAMESPACE)).thenReturn(true);

        watcher.start(COMPOSITE_ID);

        verify(consulLongPoller).startWatch(eq(COMPOSITE_STRUCTURE_KEY), any());
    }

    @Test
    void shouldNotStartLongPollWhenNotManagedByCoreOperator() {
        when(configMapClient.shouldBeManagedByCoreOperator("composite-structure", NAMESPACE)).thenReturn(false);

        watcher.start(COMPOSITE_ID);

        verify(consulLongPoller, never()).startWatch(any(), any());
    }

    @Test
    void shouldStopLongPollWhenOwnershipChanges() {
        when(configMapClient.shouldBeManagedByCoreOperator("composite-structure", NAMESPACE))
                .thenReturn(true)
                .thenReturn(false);

        watcher.start(COMPOSITE_ID); // starts long-poll
        triggerPeriodicTask(); // stops long-poll

        verify(consulLongPoller).startWatch(eq(COMPOSITE_STRUCTURE_KEY), any());
        verify(longPollSession).cancel();
    }

    @Test
    void shouldStartLongPollWhenOwnershipChangesBack() {
        LongPollSession newSession = createMockLongPollSession();
        when(configMapClient.shouldBeManagedByCoreOperator("composite-structure", NAMESPACE))
                .thenReturn(true)
                .thenReturn(false)
                .thenReturn(true);
        when(consulLongPoller.startWatch(eq(COMPOSITE_STRUCTURE_KEY), any()))
                .thenReturn(longPollSession)
                .thenReturn(newSession);

        watcher.start(COMPOSITE_ID); // starts long-poll
        triggerPeriodicTask(); // stops long-poll
        triggerPeriodicTask(); // starts long-poll again

        verify(consulLongPoller, times(2)).startWatch(eq(COMPOSITE_STRUCTURE_KEY), any());
    }

    @Test
    void shouldNotFailOnConfigMapClientException() {
        when(configMapClient.shouldBeManagedByCoreOperator("composite-structure", NAMESPACE))
                .thenThrow(new RuntimeException("API error"));

        watcher.start(COMPOSITE_ID);

        verify(consulLongPoller, never()).startWatch(any(), any());
    }

    // === Feature flag tests ===

    @Test
    void shouldNotCheckOwnershipWhenFeatureDisabled() {
        watcher = new CompositeStructureWatcher(NAMESPACE, false, consulLongPoller, configMapClient, vertx);

        watcher.start(COMPOSITE_ID);

        verifyNoInteractions(configMapClient);
        verify(consulLongPoller, never()).startWatch(any(), any());
    }

    // === Event factory tests ===

    @Test
    @SuppressWarnings("unchecked")
    void shouldUseCorrectEventFactoryForLongPoll() {
        when(configMapClient.shouldBeManagedByCoreOperator("composite-structure", NAMESPACE)).thenReturn(true);
        ArgumentCaptor<ConsulUpdateEventFactory<CompositeStructureUpdateEvent>> factoryCaptor =
                ArgumentCaptor.forClass(ConsulUpdateEventFactory.class);
        when(consulLongPoller.startWatch(eq(COMPOSITE_STRUCTURE_KEY), factoryCaptor.capture()))
                .thenReturn(longPollSession);

        watcher.start(COMPOSITE_ID);

        ConsulUpdateEventFactory<CompositeStructureUpdateEvent> factory = factoryCaptor.getValue();
        GetValue mockValue = mock(GetValue.class);
        CompositeStructureUpdateEvent event = factory.create(List.of(mockValue), 123L);

        assertThat(event).isInstanceOf(CompositeStructureUpdateEvent.class);
        assertThat(event.getValues()).containsExactly(mockValue);
        assertThat(event.getConsulIndex()).isEqualTo(123L);
    }

    // === Helper methods ===

    private void triggerPeriodicTask() {
        if (periodicHandler != null) {
            periodicHandler.accept(1L);
        }
    }

    private static LongPollSession createMockLongPollSession() {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        LongPollSession session = mock(LongPollSession.class);
        when(session.isCancelled()).thenAnswer(inv -> cancelled.get());
        org.mockito.Mockito.doAnswer(inv -> {
            cancelled.set(true);
            return null;
        }).when(session).cancel();
        return session;
    }
}
