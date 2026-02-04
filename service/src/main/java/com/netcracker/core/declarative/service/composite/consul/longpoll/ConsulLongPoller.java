package com.netcracker.core.declarative.service.composite.consul.longpoll;

import com.netcracker.cloud.consul.provider.common.TokenStorage;
import com.netcracker.cloud.quarkus.consul.client.ConsulClient;
import com.netcracker.cloud.quarkus.consul.client.ConsulSourceConfig;
import com.netcracker.cloud.quarkus.consul.client.http.QueryParams;
import com.netcracker.cloud.quarkus.consul.client.http.Response;
import com.netcracker.cloud.quarkus.consul.client.model.GetValue;
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

/**
 * Continuously long-polls a Consul KV path for changes.
 * <p>
 * On each successful poll with updated data, fires a CDI event via {@link ConsulUpdateEventFactory}.
 * Automatically retries with a delay on errors.
 * <p>
 * Returns a {@link LongPollSession} that can be used to cancel the watch loop.
 */
@Slf4j
@ApplicationScoped
public class ConsulLongPoller {

    private final TokenStorage tokenStorage;
    private final ConsulClient consulClient;
    private final ConsulSourceConfig consulSourceConfig;
    private final ConsulLongPollConfig consulLongPollConfig;
    private final Event<ConsulUpdateEvent> event;

    @Inject
    public ConsulLongPoller(Instance<TokenStorage> tokenStorage,
                            ConsulClient consulClient,
                            ConsulSourceConfig consulSourceConfig,
                            ConsulLongPollConfig consulLongPollConfig,
                            Event<ConsulUpdateEvent> event) {
        this.tokenStorage = tokenStorage.get();
        this.consulClient = consulClient;
        this.consulSourceConfig = consulSourceConfig;
        this.consulLongPollConfig = consulLongPollConfig;
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
    public <T extends ConsulUpdateEvent> LongPollSession startWatch(String root,
                                                                    ConsulUpdateEventFactory<T> factory) {
        log.info("Starting Consul long-poll watch for '/kv/{}'", root);
        LongPollSession longPollSession = new LongPollSession();

        LongPollParameters<T> longPollParameters = new LongPollParameters<>(root,
                factory,
                consulSourceConfig.waitTime(),
                consulLongPollConfig.retryTime(),
                consulLongPollConfig.onSuccessDelayTime());

        pollLoop(longPollParameters, 0, longPollSession);

        return longPollSession;
    }

    private <T extends ConsulUpdateEvent> void pollLoop(LongPollParameters<T> param,
                                                        long index,
                                                        LongPollSession longPollSession) {
        if (longPollSession.isCancelled()) {
            log.debug("Watch for '{}' was cancelled, stopping poll loop", param.root);
            return;
        }

        CompletableFuture.supplyAsync(tokenStorage::get)
                .whenCompleteAsync((String token, Throwable tokenEx) -> {
                    if (longPollSession.isCancelled()) {
                        log.debug("Watch for '{}' was cancelled after token fetch", param.root);
                        return;
                    }

                    if (tokenEx != null) {
                        log.warn("Failed to obtain token from TokenStorage. Error: {}. Retrying after {}",
                                tokenEx.getMessage(), Duration.ofMillis(param.consulRetryTimeMs));
                        scheduleNextPoll(param, index, longPollSession, param.consulRetryTimeMs);
                    } else {
                        executePoll(param, index, longPollSession, token);
                    }
                });
    }

    private <T extends ConsulUpdateEvent> void executePoll(LongPollParameters<T> param,
                                                           long index,
                                                           LongPollSession longPollSession,
                                                           String token) {
        consulClient.getKVValuesAsync(param.root, token, new QueryParams(param.waitTimeSecs, index))
                .whenCompleteAsync((response, ex) -> {
                    if (longPollSession.isCancelled()) {
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
                    scheduleNextPoll(param, nextIndex, longPollSession, retryTimeMs);
                });
    }

    private <T extends ConsulUpdateEvent> void scheduleNextPoll(LongPollParameters<T> param,
                                                                long index,
                                                                LongPollSession longPollSession,
                                                                long delayMs) {
        CompletableFuture.runAsync(
                () -> pollLoop(param, index, longPollSession),
                CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS)
        );
    }

    private <T extends ConsulUpdateEvent> void fireEvent(List<GetValue> values,
                                                         long consulIndex,
                                                         ConsulUpdateEventFactory<T> factory) {
        event.fire(factory.create(values, consulIndex));
    }

    private record LongPollParameters<T extends ConsulUpdateEvent>(String root,
                                                                   ConsulUpdateEventFactory<T> factory,
                                                                   int waitTimeSecs,
                                                                   int consulRetryTimeMs,
                                                                   int onSuccessDelayTimeMs) {
    }
}
