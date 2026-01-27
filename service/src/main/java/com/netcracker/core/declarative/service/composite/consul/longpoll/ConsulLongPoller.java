package com.netcracker.core.declarative.service.composite.consul.longpoll;

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
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Continuously long-polls a Consul KV path for changes.
 * <p>
 * On each successful poll with updated data, fires a CDI event via {@link ConsulUpdateEventFactory}.
 * Automatically retries with a delay on errors.
 * <p>
 * Returns a {@link LongPoolSession} that can be used to cancel the watch loop.
 */
@Slf4j
@ApplicationScoped
public class ConsulLongPoller {

    private static final int DEFAULT_RETRY_DELAY_MS = 20_000;
    private static final int DEFAULT_SUCCESS_DELAY_MS = 3_000;

    private final TokenStorage tokenStorage;
    private final ConsulClient consulClient;
    private final ConsulSourceConfig consulSourceConfig;
    private final Event<ConsulUpdateEvent> event;

    @Inject
    public ConsulLongPoller(Instance<TokenStorage> tokenStorage,
                            ConsulClient consulClient,
                            ConsulSourceConfig consulSourceConfig,
                            Event<ConsulUpdateEvent> event) {
        this.tokenStorage = tokenStorage.get();
        this.consulClient = consulClient;
        this.consulSourceConfig = consulSourceConfig;
        this.event = event;
    }

    /**
     * Starts watching a Consul KV path for changes.
     *
     * @param root    the Consul KV path to watch
     * @param factory factory to create typed events from Consul responses
     * @param <T>     the event type
     * @return a handle to cancel the watch
     */
    public <T extends ConsulUpdateEvent> LongPoolSession startWatchConsulRoot(String root,
                                                                              ConsulUpdateEventFactory<T> factory) {
        LongPoolSession longPoolSession = new LongPoolSession();

        LongPoolParameters<T> longPoolParameters = new LongPoolParameters<>(root,
                factory,
                consulSourceConfig.waitTime(),
                DEFAULT_RETRY_DELAY_MS,
                DEFAULT_SUCCESS_DELAY_MS);

        watchConsulRoot(longPoolParameters, 0, longPoolSession);

        return longPoolSession;
    }

    private <T extends ConsulUpdateEvent> void watchConsulRoot(LongPoolParameters<T> param,
                                                               long index,
                                                               LongPoolSession longPoolSession) {
        if (longPoolSession.isCancelled()) {
            log.debug("Watch for '{}' was cancelled, stopping poll loop", param.root);
            return;
        }

        CompletableFuture.supplyAsync(tokenStorage::get)
                .whenCompleteAsync((String token, Throwable tokenEx) -> {
                    if (longPoolSession.isCancelled()) {
                        log.debug("Watch for '{}' was cancelled after token fetch", param.root);
                        return;
                    }

                    if (tokenEx != null) {
                        log.warn("Failed to obtain token from TokenStorage. Error: {}. Retrying after {}",
                                tokenEx.getMessage(), Duration.ofMillis(param.consulRetryTimeMs));
                        scheduleNextPoll(param, index, longPoolSession, param.consulRetryTimeMs);
                    } else {
                        executePoll(param, index, longPoolSession, token);
                    }
                });
    }

    private <T extends ConsulUpdateEvent> void executePoll(LongPoolParameters<T> param,
                                                           long index,
                                                           LongPoolSession longPoolSession,
                                                           String token) {
        consulClient.getKVValuesAsync(param.root, token, new QueryParams(param.waitTimeSecs, index))
                .whenCompleteAsync((response, ex) -> {
                    if (longPoolSession.isCancelled()) {
                        log.debug("Watch for '{}' was cancelled after poll response", param.root);
                        return;
                    }

                    long nextIndex = Optional.ofNullable(response)
                            .map(Response::getConsulIndex)
                            .orElse(0L);
                    long retryTimeMs;

                    if (ex != null) {
                        retryTimeMs = param.consulRetryTimeMs;
                        log.warn("Error on long polling request to /kv/{}. Error: {}. Retrying after {}",
                                param.root, ex.getMessage(), Duration.ofMillis(retryTimeMs));
                    } else {
                        List<GetValue> values = Optional.ofNullable(response)
                                .map(Response::getValue)
                                .orElse(null);

                        if (values != null && !values.isEmpty()) {
                            retryTimeMs = param.onSuccessDelayTimeMs;
                            log.debug("Got update at '/kv/{}' with {} keys", param.root, values.size());
                            fireEvent(values, nextIndex, param.factory);
                        } else {
                            retryTimeMs = param.consulRetryTimeMs;
                            log.debug("No data at '/kv/{}', will retry after {}", param.root, Duration.ofMillis(retryTimeMs));
                        }
                    }

                    log.debug("Scheduling next poll for '/kv/{}' with index {} after {}",
                            param.root, nextIndex, Duration.ofMillis(retryTimeMs));
                    scheduleNextPoll(param, nextIndex, longPoolSession, retryTimeMs);
                });
    }

    private <T extends ConsulUpdateEvent> void scheduleNextPoll(LongPoolParameters<T> param,
                                                                long index,
                                                                LongPoolSession longPoolSession,
                                                                long delayMs) {
        CompletableFuture.runAsync(
                () -> watchConsulRoot(param, index, longPoolSession),
                CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS)
        );
    }

    private <T extends ConsulUpdateEvent> void fireEvent(List<GetValue> values,
                                                         long consulIndex,
                                                         ConsulUpdateEventFactory<T> factory) {
        event.fire(factory.create(values, consulIndex));
    }

    private record LongPoolParameters<T extends ConsulUpdateEvent>(String root,
                                                                   ConsulUpdateEventFactory<T> factory,
                                                                   int waitTimeSecs,
                                                                   int consulRetryTimeMs,
                                                                   int onSuccessDelayTimeMs) {

    }
}
