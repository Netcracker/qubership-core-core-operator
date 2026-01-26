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

/**
 * Continuously long-polls a Consul KV path for changes.
 * On each successful poll with a new modify index, invokes the snapshot consumer.
 * Automatically retries with a delay on errors.
 */
@Slf4j
@ApplicationScoped
public final class ConsulLongPoller {
    private final TokenStorage tokenStorage;
    private final ConsulClient consulClient;
    private final ConsulSourceConfig consulSourceConfig;

    @Inject
    public ConsulLongPoller(Instance<TokenStorage> tokenStorage, ConsulClient consulClient, ConsulSourceConfig consulSourceConfig) {
        this.tokenStorage = tokenStorage.get();
        this.consulClient = consulClient;
        this.consulSourceConfig = consulSourceConfig;
    }

    public <T extends ConsulUpdateEvent> void startWatchConsulRoot(String root, ConsulUpdateEventFactory<T> factory) {
        //todo vlla magic numbers
        watchConsulRoot(root, factory, consulSourceConfig.waitTime(), 20000, 3000, 0);
    }

    protected <T extends ConsulUpdateEvent> void watchConsulRoot(String root, ConsulUpdateEventFactory<T> factory, int waitTimeSecs, int consulRetryTimeMs, int onSuccessDelayTimeMs, long index) {
        CompletableFuture.supplyAsync(tokenStorage::get)
                .whenCompleteAsync((String token, Throwable tokenEx) -> {
                    if (tokenEx != null) {
                        log.warn("Failed to obtain token From TokenStorage. Error: {}. Retrying after {}",
                                tokenEx.getMessage(), Duration.ofMillis(consulRetryTimeMs));
                        CompletableFuture.runAsync(() -> watchConsulRoot(root, factory, waitTimeSecs, consulRetryTimeMs, onSuccessDelayTimeMs, index),
                                CompletableFuture.delayedExecutor(consulRetryTimeMs, TimeUnit.MILLISECONDS));
                    } else {
                        consulClient.getKVValuesAsync(root, token, new QueryParams(waitTimeSecs, index))
                                .whenCompleteAsync((response, ex) -> {
                                    long retryTimeMs;
                                    if (ex != null) {
                                        retryTimeMs = consulRetryTimeMs;
                                        log.warn("Error on long polling request to /kv/{}. Error: {}. Retrying after {}", root, ex.getMessage(), Duration.ofMillis(retryTimeMs));
                                    } else {
                                        List<GetValue> values = Optional.ofNullable(response).map(Response::getValue).orElse(null);
                                        if (values != null) {
                                            retryTimeMs = values.isEmpty() ? consulRetryTimeMs : onSuccessDelayTimeMs;
                                            log.debug("Got update at '/kv/{}' with {} updated keys:\n{}",
                                                    root, values.size(), values);
                                            firePropertiesUpdated(values, factory);
                                        } else {
                                            retryTimeMs = consulRetryTimeMs;
                                        }
                                    }
                                    // reschedule next poll
                                    Long indx = Optional.ofNullable(response).map(Response::getConsulIndex).orElse(0L);
                                    log.debug("Re-schedulling watching for Consul properties at '/kv/{}' with index: '{}' after retryTime: {}", root, indx, Duration.ofMillis(retryTimeMs));
                                    CompletableFuture.runAsync(() -> watchConsulRoot(root, factory, waitTimeSecs, consulRetryTimeMs, onSuccessDelayTimeMs, indx),
                                            CompletableFuture.delayedExecutor(retryTimeMs, TimeUnit.MILLISECONDS));
                                });
                    }
                });
    }

    protected <T extends ConsulUpdateEvent> void firePropertiesUpdated(List<GetValue> values, ConsulUpdateEventFactory<T> factory) {
        if (!values.isEmpty()) {
            Event<Object> event = Arc.container().beanManager().getEvent();
            event.fire(factory.create(values));
        }
    }
}
