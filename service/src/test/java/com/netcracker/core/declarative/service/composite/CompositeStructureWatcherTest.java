package com.netcracker.core.declarative.service.composite;

import com.netcracker.cloud.quarkus.consul.client.model.GetValue;
import com.netcracker.core.declarative.client.k8s.ConfigMapClient;
import com.netcracker.core.declarative.service.composite.consul.CompositeStructureUpdateEvent;
import com.netcracker.core.declarative.service.composite.consul.longpoll.ConsulLongPoller;
import com.netcracker.core.declarative.service.composite.consul.longpoll.ConsulUpdateEventFactory;
import com.netcracker.core.declarative.service.composite.consul.longpoll.LongPollSession;
import io.quarkus.scheduler.ScheduledExecution;
import io.quarkus.scheduler.Scheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
    private Scheduler scheduler;
    private Scheduler.JobDefinition jobDefinition;
    private LongPollSession longPollSession;
    private Consumer<ScheduledExecution> scheduledTask;

    private CompositeStructureWatcher watcher;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        consulLongPoller = mock(ConsulLongPoller.class);
        configMapClient = mock(ConfigMapClient.class);
        scheduler = mock(Scheduler.class);
        jobDefinition = mock(Scheduler.JobDefinition.class);
        longPollSession = createMockLongPollSession();

        when(scheduler.newJob(anyString())).thenReturn(jobDefinition);
        when(jobDefinition.setInterval(anyString())).thenReturn(jobDefinition);
        when(jobDefinition.setTask(any(Consumer.class))).thenAnswer(invocation -> {
            scheduledTask = invocation.getArgument(0);
            return jobDefinition;
        });
        when(consulLongPoller.startWatch(eq(COMPOSITE_STRUCTURE_KEY), any())).thenReturn(longPollSession);

        watcher = new CompositeStructureWatcher(NAMESPACE, true, consulLongPoller, configMapClient, scheduler);
    }

    // === Start lifecycle tests ===

    @Test
    void startShouldSchedulePeriodicJob() {
        watcher.start(COMPOSITE_ID);

        verify(scheduler).newJob(anyString());
        verify(jobDefinition).setInterval("5m");
        verify(jobDefinition).setTask(any(Consumer.class));
        verify(jobDefinition).schedule();
    }

    @Test
    void startShouldBeIdempotent() {
        watcher.start(COMPOSITE_ID);
        watcher.start(COMPOSITE_ID);

        verify(scheduler, times(1)).newJob(anyString());
    }

    @Test
    void startWithNullCompositeIdShouldNotStart() {
        watcher.start(null);

        verifyNoInteractions(scheduler);
    }

    @Test
    void startWithBlankCompositeIdShouldNotStart() {
        watcher.start("   ");

        verifyNoInteractions(scheduler);
    }

    // === Ownership-based long-poll control tests ===

    @Test
    void shouldStartLongPollWhenManagedByCoreOperator() {
        when(configMapClient.shouldBeManagedByCoreOperator("composite-structure", NAMESPACE)).thenReturn(true);

        watcher.start(COMPOSITE_ID);
        triggerScheduledTask();

        verify(consulLongPoller).startWatch(eq(COMPOSITE_STRUCTURE_KEY), any());
    }

    @Test
    void shouldNotStartLongPollWhenNotManagedByCoreOperator() {
        when(configMapClient.shouldBeManagedByCoreOperator("composite-structure", NAMESPACE)).thenReturn(false);

        watcher.start(COMPOSITE_ID);
        triggerScheduledTask();

        verify(consulLongPoller, never()).startWatch(any(), any());
    }

    @Test
    void shouldStopLongPollWhenOwnershipChanges() {
        when(configMapClient.shouldBeManagedByCoreOperator("composite-structure", NAMESPACE))
                .thenReturn(true)
                .thenReturn(false);

        watcher.start(COMPOSITE_ID);
        triggerScheduledTask(); // starts long-poll
        triggerScheduledTask(); // stops long-poll

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

        watcher.start(COMPOSITE_ID);
        triggerScheduledTask(); // starts long-poll
        triggerScheduledTask(); // stops long-poll
        triggerScheduledTask(); // starts long-poll again

        verify(consulLongPoller, times(2)).startWatch(eq(COMPOSITE_STRUCTURE_KEY), any());
    }

    @Test
    void shouldNotFailOnConfigMapClientException() {
        when(configMapClient.shouldBeManagedByCoreOperator("composite-structure", NAMESPACE))
                .thenThrow(new RuntimeException("API error"));

        watcher.start(COMPOSITE_ID);
        triggerScheduledTask();

        verify(consulLongPoller, never()).startWatch(any(), any());
    }

    // === Feature flag tests ===

    @Test
    void shouldNotCheckOwnershipWhenFeatureDisabled() {
        CompositeStructureWatcher disabledWatcher =
                new CompositeStructureWatcher(NAMESPACE, false, consulLongPoller, configMapClient, scheduler);

        disabledWatcher.start(COMPOSITE_ID);
        triggerScheduledTask();

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
        triggerScheduledTask();

        ConsulUpdateEventFactory<CompositeStructureUpdateEvent> factory = factoryCaptor.getValue();
        GetValue mockValue = mock(GetValue.class);
        CompositeStructureUpdateEvent event = factory.create(List.of(mockValue), 123L);

        assertThat(event).isInstanceOf(CompositeStructureUpdateEvent.class);
        assertThat(event.getValues()).containsExactly(mockValue);
        assertThat(event.getConsulIndex()).isEqualTo(123L);
    }

    // === Helper methods ===

    private void triggerScheduledTask() {
        if (scheduledTask != null) {
            scheduledTask.accept(null);
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
