package com.netcracker.core.declarative.service.composite;

import com.netcracker.cloud.quarkus.consul.client.model.GetValue;
import com.netcracker.core.declarative.client.k8s.ConfigMapClient;
import com.netcracker.core.declarative.service.composite.consul.CompositeStructureUpdateEvent;
import com.netcracker.core.declarative.service.composite.consul.longpoll.ConsulLongPoller;
import com.netcracker.core.declarative.service.composite.consul.longpoll.ConsulUpdateEventFactory;
import com.netcracker.core.declarative.service.composite.consul.longpoll.LongPollSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
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
    private static final long TEST_CHECK_INTERVAL_MS = 50;

    private ConsulLongPoller consulLongPoller;
    private ConfigMapClient configMapClient;
    private LongPollSession longPollSession;

    private CompositeStructureWatcher watcher;

    @BeforeEach
    void setUp() {
        consulLongPoller = mock(ConsulLongPoller.class);
        configMapClient = mock(ConfigMapClient.class);
        longPollSession = createMockLongPollSession();

        when(consulLongPoller.startWatch(eq(COMPOSITE_STRUCTURE_KEY), any())).thenReturn(longPollSession);

        watcher = new CompositeStructureWatcher(NAMESPACE, true, TEST_CHECK_INTERVAL_MS, consulLongPoller, configMapClient);
    }

    // === Start lifecycle tests ===

    @Test
    void startShouldBeIdempotent() {
        when(configMapClient.shouldBeManagedByCoreOperator("composite-structure", NAMESPACE)).thenReturn(true);

        watcher.start(COMPOSITE_ID);
        watcher.start(COMPOSITE_ID);

        await().untilAsserted(() ->
                verify(configMapClient, atLeast(1)).shouldBeManagedByCoreOperator("composite-structure", NAMESPACE));
        verify(consulLongPoller, times(1)).startWatch(eq(COMPOSITE_STRUCTURE_KEY), any());
    }

    @Test
    void startWithNullCompositeIdShouldNotStart() {
        watcher.start(null);

        verifyNoInteractions(configMapClient);
    }

    @Test
    void startWithBlankCompositeIdShouldNotStart() {
        watcher.start("   ");

        verifyNoInteractions(configMapClient);
    }

    // === Ownership-based long-poll control tests ===

    @Test
    void shouldStartLongPollWhenManagedByCoreOperator() {
        when(configMapClient.shouldBeManagedByCoreOperator("composite-structure", NAMESPACE)).thenReturn(true);

        watcher.start(COMPOSITE_ID);

        await().untilAsserted(() ->
                verify(consulLongPoller).startWatch(eq(COMPOSITE_STRUCTURE_KEY), any()));
    }

    @Test
    void shouldNotStartLongPollWhenNotManagedByCoreOperator() {
        when(configMapClient.shouldBeManagedByCoreOperator("composite-structure", NAMESPACE)).thenReturn(false);

        watcher.start(COMPOSITE_ID);

        await().untilAsserted(() ->
                verify(configMapClient, atLeast(1)).shouldBeManagedByCoreOperator("composite-structure", NAMESPACE));
        verify(consulLongPoller, never()).startWatch(any(), any());
    }

    @Test
    void shouldStopLongPollWhenOwnershipChanges() {
        when(configMapClient.shouldBeManagedByCoreOperator("composite-structure", NAMESPACE))
                .thenReturn(true)
                .thenReturn(false);

        watcher.start(COMPOSITE_ID);

        await().untilAsserted(() -> verify(longPollSession).cancel());
    }

    @Test
    void shouldNotFailOnConfigMapClientException() {
        when(configMapClient.shouldBeManagedByCoreOperator("composite-structure", NAMESPACE))
                .thenThrow(new RuntimeException("API error"));

        watcher.start(COMPOSITE_ID);

        await().untilAsserted(() ->
                verify(configMapClient, atLeast(1)).shouldBeManagedByCoreOperator("composite-structure", NAMESPACE));
        verify(consulLongPoller, never()).startWatch(any(), any());
    }

    // === Feature flag tests ===

    @Test
    void shouldNotCheckOwnershipWhenFeatureDisabled() {
        watcher = new CompositeStructureWatcher(NAMESPACE, false, TEST_CHECK_INTERVAL_MS, consulLongPoller, configMapClient);

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

        await().untilAsserted(() ->
                verify(consulLongPoller).startWatch(eq(COMPOSITE_STRUCTURE_KEY), any()));

        ConsulUpdateEventFactory<CompositeStructureUpdateEvent> factory = factoryCaptor.getValue();
        GetValue mockValue = mock(GetValue.class);
        CompositeStructureUpdateEvent event = factory.create(List.of(mockValue), 123L);

        assertThat(event).isInstanceOf(CompositeStructureUpdateEvent.class);
        assertThat(event.getValues()).containsExactly(mockValue);
        assertThat(event.getConsulIndex()).isEqualTo(123L);
    }

    // === Helper methods ===

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
