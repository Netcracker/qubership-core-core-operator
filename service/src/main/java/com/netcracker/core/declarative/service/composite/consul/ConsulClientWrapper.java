package com.netcracker.core.declarative.service.composite.consul;

import com.netcracker.cloud.consul.provider.common.TokenStorage;
import com.netcracker.core.declarative.service.ConsulClientFactory;
import com.netcracker.core.declarative.service.composite.consul.longpoll.PollResultHandler;
import com.netcracker.core.declarative.service.composite.consul.model.ConsulPrefixSnapshot;
import io.vertx.core.AsyncResult;
import io.vertx.ext.consul.BlockingQueryOptions;
import io.vertx.ext.consul.KeyValueList;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

/**
 * {@link ConsulClient} implementation backed by Vert.x consul client.
 * Creates a short-lived Vert.x client per request, performs a blocking query (long-poll),
 * and translates the async result into {@link PollResultHandler} callbacks.
 */
@ApplicationScoped
@Slf4j
public final class ConsulClientWrapper implements ConsulClient {
    private static final long DEFAULT_READ_TIMEOUT_MILLIS = Duration.ofMinutes(10).toMillis();

    private final ConsulClientFactory consulClientFactory;
    private final TokenStorage tokenStorage;

    @Inject
    public ConsulClientWrapper(ConsulClientFactory consulClientFactory,
                               Instance<TokenStorage> tokenStorage) {
        this.consulClientFactory = consulClientFactory;
        this.tokenStorage = tokenStorage.get();
    }

    @Override
    public void awaitChanges(String path, long index, Duration wait, PollResultHandler handler) {
        BlockingQueryOptions bq = new BlockingQueryOptions()
                .setIndex(index)
                .setWait(format(wait));

        String token = tokenStorage.get();
        io.vertx.ext.consul.ConsulClient consulClient = consulClientFactory.create(token, DEFAULT_READ_TIMEOUT_MILLIS);
        log.debug("Await values from consul. path='{}', requestIndex={}, wait={}", path, index, wait);
        try {
            consulClient.getValuesWithOptions(path, bq, ar -> {
                try {
                    handle(path, handler, ar);
                } finally {
                    consulClient.close();
                }
            });
        } catch (Exception e) {
            consulClient.close();
            throw e;
        }
    }

    void handle(String path,
                PollResultHandler handler,
                AsyncResult<KeyValueList> ar) {
        if (ar.succeeded()) {
            log.debug("Consul long-poll succeeded: path='{}' -> proceed snapshot", path);
            ConsulPrefixSnapshot snapshot = new ConsulPrefixSnapshot(ar.result());
            handler.onSuccess(snapshot);
        } else {
            Throwable cause = ar.cause();
            log.warn("Consul long-poll failed: path='{}'", path, cause);
            handler.onError(cause);
        }
    }

    static String format(Duration wait) {
        long seconds = Math.max(1, (long) Math.ceil(wait.toMillis() / 1000.0));
        return seconds + "s";
    }
}
