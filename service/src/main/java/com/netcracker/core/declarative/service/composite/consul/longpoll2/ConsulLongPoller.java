package com.netcracker.core.declarative.service.composite.consul.longpoll2;

import com.netcracker.cloud.consul.provider.common.TokenStorage;
import com.netcracker.cloud.quarkus.consul.client.ConsulClient;
import com.netcracker.cloud.quarkus.consul.client.ConsulSourceConfig;
import com.netcracker.cloud.quarkus.consul.client.http.QueryParams;
import com.netcracker.cloud.quarkus.consul.client.http.Response;
import com.netcracker.cloud.quarkus.consul.client.model.GetValue;
import io.quarkus.arc.Arc;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Continuously long-polls a Consul KV path for changes.
 * <p>
 * On each successful poll with updated data, fires a CDI event via {@link ConsulUpdateEventFactory}.
 * Automatically retries with a delay on errors.
 * <p>
 * Returns a {@link WatchHandle} that can be used to cancel the watch loop.
 */
@Slf4j
@ApplicationScoped
public class ConsulLongPoller {

    private static final int DEFAULT_RETRY_DELAY_MS = 20_000;
    private static final int DEFAULT_SUCCESS_DELAY_MS = 3_000;

    private final TokenStorage tokenStorage;
    private final ConsulClient consulClient;
    private final ConsulSourceConfig consulSourceConfig;

    @Inject
    public ConsulLongPoller(Instance<TokenStorage> tokenStorage,
                            ConsulClient consulClient,
                            ConsulSourceConfig consulSourceConfig) {
        this.tokenStorage = tokenStorage.get();
        this.consulClient = consulClient;
        this.consulSourceConfig = consulSourceConfig;
    }

    /**
     * Starts watching a Consul KV path for changes.
     *
     * @param root    the Consul KV path to watch
     * @param factory factory to create typed events from Consul responses
     * @param <T>     the event type
     * @return a handle to cancel the watch
     */
    public <T extends ConsulUpdateEvent> WatchHandle startWatchConsulRoot(String root,
                                                                          ConsulUpdateEventFactory<T> factory) {
        WatchHandle handle = new WatchHandleImpl();

        watchConsulRoot(root, factory, consulSourceConfig.waitTime(),
                DEFAULT_RETRY_DELAY_MS, DEFAULT_SUCCESS_DELAY_MS, 0, handle);

        return handle;
    }

    private <T extends ConsulUpdateEvent> void watchConsulRoot(String root,
                                                               ConsulUpdateEventFactory<T> factory,
                                                               int waitTimeSecs,
                                                               int consulRetryTimeMs,
                                                               int onSuccessDelayTimeMs,
                                                               long index,
                                                               WatchHandle handle) {
        if (handle.isCancelled()) {
            log.debug("Watch for '{}' was cancelled, stopping poll loop", root);
            return;
        }

        CompletableFuture.supplyAsync(tokenStorage::get)
                .whenCompleteAsync((String token, Throwable tokenEx) -> {
                    if (handle.isCancelled()) {
                        log.debug("Watch for '{}' was cancelled after token fetch", root);
                        return;
                    }

                    if (tokenEx != null) {
                        log.warn("Failed to obtain token from TokenStorage. Error: {}. Retrying after {}",
                                tokenEx.getMessage(), Duration.ofMillis(consulRetryTimeMs));
                        scheduleNextPoll(root, factory, waitTimeSecs, consulRetryTimeMs,
                                onSuccessDelayTimeMs, index, handle, consulRetryTimeMs);
                    } else {
                        executePoll(root, factory, waitTimeSecs, consulRetryTimeMs,
                                onSuccessDelayTimeMs, index, handle, token);
                    }
                });
    }

    private <T extends ConsulUpdateEvent> void executePoll(String root,
                                                           ConsulUpdateEventFactory<T> factory,
                                                           int waitTimeSecs,
                                                           int consulRetryTimeMs,
                                                           int onSuccessDelayTimeMs,
                                                           long index,
                                                           WatchHandle handle,
                                                           String token) {
        consulClient.getKVValuesAsync(root, token, new QueryParams(waitTimeSecs, index))
                .whenCompleteAsync((response, ex) -> {
                    if (handle.isCancelled()) {
                        log.debug("Watch for '{}' was cancelled after poll response", root);
                        return;
                    }

                    long nextIndex = Optional.ofNullable(response)
                            .map(Response::getConsulIndex)
                            .orElse(0L);
                    long retryTimeMs;

                    if (ex != null) {
                        retryTimeMs = consulRetryTimeMs;
                        log.warn("Error on long polling request to /kv/{}. Error: {}. Retrying after {}",
                                root, ex.getMessage(), Duration.ofMillis(retryTimeMs));
                    } else {
                        List<GetValue> values = Optional.ofNullable(response)
                                .map(Response::getValue)
                                .orElse(null);

                        if (values != null && !values.isEmpty()) {
                            retryTimeMs = onSuccessDelayTimeMs;
                            log.debug("Got update at '/kv/{}' with {} keys", root, values.size());
                            fireEvent(values, nextIndex, factory);
                        } else {
                            retryTimeMs = consulRetryTimeMs;
                            log.debug("No data at '/kv/{}', will retry after {}", root, Duration.ofMillis(retryTimeMs));
                        }
                    }

                    log.debug("Scheduling next poll for '/kv/{}' with index {} after {}",
                            root, nextIndex, Duration.ofMillis(retryTimeMs));
                    scheduleNextPoll(root, factory, waitTimeSecs, consulRetryTimeMs,
                            onSuccessDelayTimeMs, nextIndex, handle, retryTimeMs);
                });
    }

    private <T extends ConsulUpdateEvent> void scheduleNextPoll(String root,
                                                                ConsulUpdateEventFactory<T> factory,
                                                                int waitTimeSecs,
                                                                int consulRetryTimeMs,
                                                                int onSuccessDelayTimeMs,
                                                                long index,
                                                                WatchHandle handle,
                                                                long delayMs) {
        CompletableFuture.runAsync(
                () -> watchConsulRoot(root, factory, waitTimeSecs, consulRetryTimeMs,
                        onSuccessDelayTimeMs, index, handle),
                CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS)
        );
    }

    private <T extends ConsulUpdateEvent> void fireEvent(List<GetValue> values,
                                                         long consulIndex,
                                                         ConsulUpdateEventFactory<T> factory) {
        Event<Object> event = Arc.container().beanManager().getEvent();
        event.fire(factory.create(values, consulIndex));
    }

    private static class WatchHandleImpl implements WatchHandle {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        @Override
        public void cancel() {
            cancelled.set(true);
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }
    }
}
