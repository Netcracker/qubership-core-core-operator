package com.netcracker.core.declarative.client.cache;

import com.netcracker.core.declarative.resources.base.Phase;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.UpdatableCache;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.netcracker.core.declarative.client.constants.Constants.CR_RETRY_MAX_INTERVAL;

public class RetryResourceCache implements UpdatableCache<Integer> {
    private final Map<ResourceID, Integer> retryCache = new HashMap<>();

    @Override
    public Integer remove(ResourceID key) {
        return retryCache.remove(key);
    }

    @Override
    public void put(ResourceID key, Integer retries) {
        retryCache.put(key, retries);
    }

    @Override
    public Optional<Integer> get(ResourceID resourceID) {
        return Optional.ofNullable(retryCache.get(resourceID));
    }

    @Override
    public Stream<ResourceID> keys() {
        return retryCache.keySet().stream();
    }

    @Override
    public Stream<Integer> list(Predicate<Integer> predicate) {
        return retryCache.values().stream().filter(predicate);
    }

    public int getNextDelay(Phase phase, ResourceID resourceID) {
        Optional<Integer> retries = get(resourceID);
        if (retries.isPresent() && (phase == Phase.WAITING_FOR_DEPENDS || phase == Phase.BACKING_OFF)) {
            retryCache.put(resourceID, retries.get() + 1);
            return (int) Math.min(2 * Math.pow(2, (double) retries.get() / 2), CR_RETRY_MAX_INTERVAL);
        } else {
            put(resourceID, 1);
            return 1;
        }
    }
}
