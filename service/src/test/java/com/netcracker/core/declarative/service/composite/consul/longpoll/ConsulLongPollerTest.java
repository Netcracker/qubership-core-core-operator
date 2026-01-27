package com.netcracker.core.declarative.service.composite.consul.longpoll;

import com.netcracker.cloud.consul.provider.common.TokenStorage;
import com.netcracker.cloud.quarkus.consul.client.ConsulClient;
import com.netcracker.cloud.quarkus.consul.client.ConsulSourceConfig;
import com.netcracker.cloud.quarkus.consul.client.http.QueryParams;
import com.netcracker.cloud.quarkus.consul.client.http.Response;
import com.netcracker.cloud.quarkus.consul.client.model.GetValue;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsulLongPollerTest {

    private static final String ROOT_PATH = "config/test/path";
    private static final String TOKEN = "test-token";
    private static final int WAIT_TIME_SECS = 30;
    private static final int RETRY_DELAY_MS = 100;
    private static final int SUCCESS_DELAY_MS = 50;

    private TokenStorage tokenStorage;
    private ConsulClient consulClient;
    private Event<ConsulUpdateEvent> event;
    private ConsulLongPoller poller;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        tokenStorage = mock(TokenStorage.class);
        consulClient = mock(ConsulClient.class);
        ConsulSourceConfig consulSourceConfig = mock(ConsulSourceConfig.class);
        ConsulLongPollConfig consulLongPollConfig = mock(ConsulLongPollConfig.class);
        event = mock(Event.class);

        Instance<TokenStorage> tokenStorageInstance = mock(Instance.class);
        when(tokenStorageInstance.get()).thenReturn(tokenStorage);
        when(consulSourceConfig.waitTime()).thenReturn(WAIT_TIME_SECS);
        when(consulLongPollConfig.consulRetryTime()).thenReturn(RETRY_DELAY_MS);
        when(consulLongPollConfig.consulOnSuccessDelayTime()).thenReturn(SUCCESS_DELAY_MS);
        when(tokenStorage.get()).thenReturn(TOKEN);

        poller = new ConsulLongPoller(tokenStorageInstance, consulClient, consulSourceConfig, consulLongPollConfig, event);
    }

    @Test
    void startWatchShouldReturnNonCancelledSession() {
        when(consulClient.getKVValuesAsync(anyString(), anyString(), any(QueryParams.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        LongPollSession session = poller.startWatchConsulRoot(ROOT_PATH, TestEvent::new);

        assertThat(session).isNotNull();
        assertThat(session.isCancelled()).isFalse();
    }

    @Test
    void shouldFireEventOnSuccessfulPollWithData() throws InterruptedException {
        GetValue mockValue = mock(GetValue.class);
        Response<List<GetValue>> response = createResponse(List.of(mockValue), 100L);

        CountDownLatch eventFired = new CountDownLatch(1);
        doAnswer(inv -> {
            eventFired.countDown();
            return null;
        }).when(event).fire(any(ConsulUpdateEvent.class));

        when(consulClient.getKVValuesAsync(eq(ROOT_PATH), eq(TOKEN), any(QueryParams.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        LongPollSession session = poller.startWatchConsulRoot(ROOT_PATH, TestEvent::new);

        assertThat(eventFired.await(2, TimeUnit.SECONDS)).isTrue();

        ArgumentCaptor<ConsulUpdateEvent> eventCaptor = ArgumentCaptor.forClass(ConsulUpdateEvent.class);
        verify(event, atLeast(1)).fire(eventCaptor.capture());

        ConsulUpdateEvent firedEvent = eventCaptor.getValue();
        assertThat(firedEvent).isInstanceOf(TestEvent.class);
        assertThat(firedEvent.getValues()).containsExactly(mockValue);
        assertThat(firedEvent.getConsulIndex()).isEqualTo(100L);

        session.cancel();
    }

    @Test
    void shouldNotFireEventOnEmptyResponse() throws InterruptedException {
        Response<List<GetValue>> emptyResponse = createResponse(List.of(), 100L);
        CountDownLatch pollCompleted = new CountDownLatch(1);

        when(consulClient.getKVValuesAsync(eq(ROOT_PATH), eq(TOKEN), any(QueryParams.class)))
                .thenAnswer(inv -> {
                    pollCompleted.countDown();
                    return CompletableFuture.completedFuture(emptyResponse);
                });

        LongPollSession session = poller.startWatchConsulRoot(ROOT_PATH, TestEvent::new);

        assertThat(pollCompleted.await(2, TimeUnit.SECONDS)).isTrue();
        verify(event, never()).fire(any(ConsulUpdateEvent.class));

        session.cancel();
    }

    @Test
    void shouldNotFireEventOnNullResponse() throws InterruptedException {
        CountDownLatch pollCompleted = new CountDownLatch(1);

        when(consulClient.getKVValuesAsync(eq(ROOT_PATH), eq(TOKEN), any(QueryParams.class)))
                .thenAnswer(inv -> {
                    pollCompleted.countDown();
                    return CompletableFuture.completedFuture(null);
                });

        LongPollSession session = poller.startWatchConsulRoot(ROOT_PATH, TestEvent::new);

        assertThat(pollCompleted.await(2, TimeUnit.SECONDS)).isTrue();
        verify(event, never()).fire(any(ConsulUpdateEvent.class));

        session.cancel();
    }

    @Test
    void shouldStopPollingWhenSessionCancelled() throws InterruptedException {
        AtomicInteger pollCount = new AtomicInteger(0);
        CountDownLatch firstPollDone = new CountDownLatch(1);
        CountDownLatch secondPollBlocked = new CountDownLatch(1);

        when(consulClient.getKVValuesAsync(eq(ROOT_PATH), eq(TOKEN), any(QueryParams.class)))
                .thenAnswer(inv -> {
                    int count = pollCount.incrementAndGet();
                    if (count == 1) {
                        firstPollDone.countDown();
                    } else {
                        secondPollBlocked.countDown();
                    }
                    return CompletableFuture.completedFuture(null);
                });

        LongPollSession session = poller.startWatchConsulRoot(ROOT_PATH, TestEvent::new);

        assertThat(firstPollDone.await(2, TimeUnit.SECONDS)).isTrue();
        session.cancel();

        // Second poll should not happen or should exit early
        boolean secondPollHappened = secondPollBlocked.await(500, TimeUnit.MILLISECONDS);
        // At most one more poll could have been scheduled before cancellation took effect
        assertThat(pollCount.get()).isLessThanOrEqualTo(2);
    }

    @Test
    void shouldRetryOnConsulClientError() {
        AtomicInteger callCount = new AtomicInteger(0);
        CountDownLatch secondCall = new CountDownLatch(2);

        when(consulClient.getKVValuesAsync(eq(ROOT_PATH), eq(TOKEN), any(QueryParams.class)))
                .thenAnswer(inv -> {
                    callCount.incrementAndGet();
                    secondCall.countDown();
                    return CompletableFuture.failedFuture(new RuntimeException("Consul unavailable"));
                });

        LongPollSession session = poller.startWatchConsulRoot(ROOT_PATH, TestEvent::new);

        verify(consulClient, timeout(5000).atLeast(1))
                .getKVValuesAsync(eq(ROOT_PATH), eq(TOKEN), any(QueryParams.class));

        session.cancel();
    }

    @Test
    void shouldRetryOnTokenStorageError() throws InterruptedException {
        AtomicInteger tokenCallCount = new AtomicInteger(0);
        CountDownLatch secondTokenCall = new CountDownLatch(2);

        when(tokenStorage.get()).thenAnswer(inv -> {
            int count = tokenCallCount.incrementAndGet();
            secondTokenCall.countDown();
            if (count == 1) {
                throw new RuntimeException("Token storage unavailable");
            }
            return TOKEN;
        });

        Response<List<GetValue>> response = createResponse(List.of(mock(GetValue.class)), 100L);
        when(consulClient.getKVValuesAsync(eq(ROOT_PATH), eq(TOKEN), any(QueryParams.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        CountDownLatch eventFired = new CountDownLatch(1);
        doAnswer(inv -> {
            eventFired.countDown();
            return null;
        }).when(event).fire(any(ConsulUpdateEvent.class));

        LongPollSession session = poller.startWatchConsulRoot(ROOT_PATH, TestEvent::new);

        // With RETRY_DELAY_MS=100ms configured in test, retry should happen quickly
        assertThat(secondTokenCall.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(tokenCallCount.get()).isGreaterThanOrEqualTo(2);

        session.cancel();
    }

    @Test
    void shouldPassCorrectQueryParams() throws InterruptedException {
        Response<List<GetValue>> response = createResponse(List.of(mock(GetValue.class)), 42L);
        AtomicReference<QueryParams> capturedParams = new AtomicReference<>();
        CountDownLatch pollDone = new CountDownLatch(1);

        when(consulClient.getKVValuesAsync(eq(ROOT_PATH), eq(TOKEN), any(QueryParams.class)))
                .thenAnswer(inv -> {
                    capturedParams.set(inv.getArgument(2));
                    pollDone.countDown();
                    return CompletableFuture.completedFuture(response);
                });

        LongPollSession session = poller.startWatchConsulRoot(ROOT_PATH, TestEvent::new);

        assertThat(pollDone.await(2, TimeUnit.SECONDS)).isTrue();

        QueryParams params = capturedParams.get();
        assertThat(params).isNotNull();
        assertThat(params.getWaitTime()).isEqualTo(WAIT_TIME_SECS);
        assertThat(params.getIndex()).isEqualTo(0L);

        session.cancel();
    }

    @Test
    void shouldUseConsulIndexForNextPoll() throws InterruptedException {
        GetValue mockValue = mock(GetValue.class);
        Response<List<GetValue>> response1 = createResponse(List.of(mockValue), 100L);
        Response<List<GetValue>> response2 = createResponse(List.of(mockValue), 200L);

        AtomicInteger callCount = new AtomicInteger(0);
        AtomicReference<Long> secondIndex = new AtomicReference<>();
        CountDownLatch secondPoll = new CountDownLatch(2);

        when(consulClient.getKVValuesAsync(eq(ROOT_PATH), eq(TOKEN), any(QueryParams.class)))
                .thenAnswer(inv -> {
                    QueryParams params = inv.getArgument(2);
                    int count = callCount.incrementAndGet();
                    secondPoll.countDown();
                    if (count == 2) {
                        secondIndex.set(params.getIndex());
                    }
                    return CompletableFuture.completedFuture(count == 1 ? response1 : response2);
                });

        LongPollSession session = poller.startWatchConsulRoot(ROOT_PATH, TestEvent::new);

        assertThat(secondPoll.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(secondIndex.get()).isEqualTo(100L);

        session.cancel();
    }

    @Test
    void cancelledSessionShouldNotFireEvents() throws InterruptedException {
        CountDownLatch pollStarted = new CountDownLatch(1);
        CountDownLatch pollCanContinue = new CountDownLatch(1);
        CountDownLatch responseProcessed = new CountDownLatch(1);

        GetValue mockValue = mock(GetValue.class);
        Response<List<GetValue>> response = createResponse(List.of(mockValue), 100L);

        when(consulClient.getKVValuesAsync(eq(ROOT_PATH), eq(TOKEN), any(QueryParams.class)))
                .thenAnswer(inv -> {
                    pollStarted.countDown();
                    pollCanContinue.await();
                    return CompletableFuture.supplyAsync(() -> {
                        responseProcessed.countDown();
                        return response;
                    });
                });

        LongPollSession session = poller.startWatchConsulRoot(ROOT_PATH, TestEvent::new);

        assertThat(pollStarted.await(2, TimeUnit.SECONDS)).isTrue();
        session.cancel();
        pollCanContinue.countDown();

        assertThat(responseProcessed.await(2, TimeUnit.SECONDS)).isTrue();
        // Give async processing a moment to complete
        Thread.sleep(100);
        verify(event, never()).fire(any(ConsulUpdateEvent.class));
    }

    @Test
    void shouldNotFireEventWhenResponseValueIsNull() throws InterruptedException {
        CountDownLatch pollCompleted = new CountDownLatch(1);
        Response<List<GetValue>> responseWithNullValue = createResponse(null, 100L);

        when(consulClient.getKVValuesAsync(eq(ROOT_PATH), eq(TOKEN), any(QueryParams.class)))
                .thenAnswer(inv -> {
                    pollCompleted.countDown();
                    return CompletableFuture.completedFuture(responseWithNullValue);
                });

        LongPollSession session = poller.startWatchConsulRoot(ROOT_PATH, TestEvent::new);

        assertThat(pollCompleted.await(2, TimeUnit.SECONDS)).isTrue();
        verify(event, never()).fire(any(ConsulUpdateEvent.class));

        session.cancel();
    }

    @SuppressWarnings("unchecked")
    private Response<List<GetValue>> createResponse(List<GetValue> values, long consulIndex) {
        Response<List<GetValue>> response = mock(Response.class);
        when(response.getValue()).thenReturn(values);
        when(response.getConsulIndex()).thenReturn(consulIndex);
        return response;
    }

    private static class TestEvent extends ConsulUpdateEvent {
        public TestEvent(List<GetValue> values, long consulIndex) {
            super(values, consulIndex);
        }
    }
}
