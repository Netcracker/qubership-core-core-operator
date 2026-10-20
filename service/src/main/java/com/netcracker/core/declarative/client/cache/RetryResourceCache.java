package com.netcracker.core.declarative.client.cache;

import com.netcracker.core.declarative.resources.base.Phase;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

import static com.netcracker.core.declarative.client.constants.Constants.CR_POLLING_MAX_INTERVAL;
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
                RetryState state = update(resourceID, RetryState::withPollingAttempt);
                yield delay(state.pollingAttempts() - 1, CR_POLLING_MAX_INTERVAL);
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
     * first status request must not depend on how many apply attempts failed before, and a new apply
     * attempt restarts status polling, so a new operation is not polled with the delay of a previous one.
     */
    private record RetryState(int applyAttempts, int pollingAttempts) {
        private static final RetryState NO_ATTEMPTS = new RetryState(0, 0);
        private static final RetryState FIRST_ATTEMPT = new RetryState(1, 1);

        private RetryState withApplyAttempt() {
            // a new apply may start a new operation, whose polling must not inherit the delay of the previous one
            return new RetryState(applyAttempts + 1, Math.min(pollingAttempts, FIRST_ATTEMPT.pollingAttempts));
        }

        private RetryState withPollingAttempt() {
            return new RetryState(applyAttempts, pollingAttempts + 1);
        }
    }
}
