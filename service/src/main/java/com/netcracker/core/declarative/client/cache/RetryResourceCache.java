package com.netcracker.core.declarative.client.cache;

import com.netcracker.core.declarative.resources.base.Phase;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

import static com.netcracker.core.declarative.client.constants.Constants.CR_POOLING_MAX_INTERVAL;
import static com.netcracker.core.declarative.client.constants.Constants.CR_RETRY_MAX_INTERVAL;

/**
 * Keeps track of how many times a CR has been processed without reaching a terminal phase and
 * calculates the delay before the next reconciliation.
 */
public class RetryResourceCache {
    private final Map<ResourceID, RetryState> retryCache = new ConcurrentHashMap<>();

    /**
     * Drops the attempt counters of a resource, so that its next reconciliation starts from the
     * shortest delay. Called once the resource reaches a terminal phase.
     */
    public void reset(ResourceID resourceID) {
        retryCache.remove(resourceID);
    }

    public int getNextDelay(Phase phase, ResourceID resourceID) {
        return switch (phase) {
            case BACKING_OFF -> {
                RetryState state = update(resourceID, RetryState::withApplyAttempt);
                yield delay(state.applyAttempts() - 1, CR_RETRY_MAX_INTERVAL);
            }
            case WAITING_FOR_DEPENDS -> {
                RetryState state = update(resourceID, RetryState::withPoolingAttempt);
                yield delay(state.poolingAttempts() - 1, CR_POOLING_MAX_INTERVAL);
            }
            default -> {
                //any other phase starts a new processing attempt, both counters start from scratch
                retryCache.put(resourceID, RetryState.FIRST_ATTEMPT);
                yield 1;
            }
        };
    }

    private RetryState update(ResourceID resourceID, UnaryOperator<RetryState> attempt) {
        return retryCache.compute(resourceID, (id, state) -> attempt.apply(state == null ? RetryState.NO_ATTEMPTS : state));
    }

    private static int delay(int previousAttempts, double maxInterval) {
        if (previousAttempts == 0) {
            return 1;
        }
        return (int) Math.min(2 * Math.pow(2, (double) previousAttempts / 2), maxInterval);
    }

    /**
     * Attempt counters of a single resource. Attempts to apply a CR that failed and status requests
     * of an operation that has already been accepted are counted separately: the delay before the
     * first status request must not depend on how many apply attempts failed before.
     */
    private record RetryState(int applyAttempts, int poolingAttempts) {
        private static final RetryState NO_ATTEMPTS = new RetryState(0, 0);
        private static final RetryState FIRST_ATTEMPT = new RetryState(1, 1);

        private RetryState withApplyAttempt() {
            return new RetryState(applyAttempts + 1, poolingAttempts);
        }

        private RetryState withPoolingAttempt() {
            return new RetryState(applyAttempts, poolingAttempts + 1);
        }
    }
}
