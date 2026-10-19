package com.netcracker.core.declarative.client.cache;

import com.netcracker.core.declarative.resources.base.Phase;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import org.junit.jupiter.api.Test;

import static com.netcracker.core.declarative.client.constants.Constants.CR_POOLING_MAX_INTERVAL;
import static com.netcracker.core.declarative.client.constants.Constants.CR_RETRY_MAX_ATTEMPTS;
import static com.netcracker.core.declarative.client.constants.Constants.CR_RETRY_MAX_INTERVAL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetryResourceCacheTest {
    private static final ResourceID RESOURCE_ID = new ResourceID("my-declaration", "my-namespace");

    @Test
    void failedApplyAttemptsDoNotInflateFirstStatusRequest() {
        RetryResourceCache cache = new RetryResourceCache();

        cache.getNextDelay(Phase.UPDATING, RESOURCE_ID);
        for (int i = 0; i < 12; i++) {
            cache.getNextDelay(Phase.BACKING_OFF, RESOURCE_ID);
        }
        //apply finally accepted with 202, CR moves to WaitingForDependency
        assertEquals(2, cache.getNextDelay(Phase.WAITING_FOR_DEPENDS, RESOURCE_ID));
    }

    @Test
    void backingOffDelayGrowsAndIsCapped() {
        RetryResourceCache cache = new RetryResourceCache();

        cache.getNextDelay(Phase.UPDATING, RESOURCE_ID);
        assertEquals(2, cache.getNextDelay(Phase.BACKING_OFF, RESOURCE_ID));
        assertEquals(4, cache.getNextDelay(Phase.BACKING_OFF, RESOURCE_ID));
        assertEquals(5, cache.getNextDelay(Phase.BACKING_OFF, RESOURCE_ID));
        assertEquals(8, cache.getNextDelay(Phase.BACKING_OFF, RESOURCE_ID));

        for (int i = 0; i < 100; i++) {
            cache.getNextDelay(Phase.BACKING_OFF, RESOURCE_ID);
        }
        assertEquals((int) CR_RETRY_MAX_INTERVAL, cache.getNextDelay(Phase.BACKING_OFF, RESOURCE_ID));
    }

    @Test
    void poolingDelayGrowsAndIsCappedSeparately() {
        RetryResourceCache cache = new RetryResourceCache();

        assertEquals(1, cache.getNextDelay(Phase.WAITING_FOR_DEPENDS, RESOURCE_ID));
        assertEquals(2, cache.getNextDelay(Phase.WAITING_FOR_DEPENDS, RESOURCE_ID));
        assertEquals(4, cache.getNextDelay(Phase.WAITING_FOR_DEPENDS, RESOURCE_ID));

        for (int i = 0; i < 20; i++) {
            cache.getNextDelay(Phase.WAITING_FOR_DEPENDS, RESOURCE_ID);
        }
        assertEquals((int) CR_POOLING_MAX_INTERVAL, cache.getNextDelay(Phase.WAITING_FOR_DEPENDS, RESOURCE_ID));
        assertTrue(CR_POOLING_MAX_INTERVAL < CR_RETRY_MAX_INTERVAL);
    }

    @Test
    void poolingAttemptsDoNotResetBackingOffDelay() {
        RetryResourceCache cache = new RetryResourceCache();

        cache.getNextDelay(Phase.UPDATING, RESOURCE_ID);
        assertEquals(2, cache.getNextDelay(Phase.BACKING_OFF, RESOURCE_ID));
        assertEquals(4, cache.getNextDelay(Phase.BACKING_OFF, RESOURCE_ID));

        //operation accepted, then a status request fails and the CR is re-applied
        cache.getNextDelay(Phase.WAITING_FOR_DEPENDS, RESOURCE_ID);
        assertEquals(5, cache.getNextDelay(Phase.BACKING_OFF, RESOURCE_ID));
    }

    @Test
    void terminalPhaseResetsBothCounters() {
        RetryResourceCache cache = new RetryResourceCache();

        for (int i = 0; i < 5; i++) {
            cache.getNextDelay(Phase.BACKING_OFF, RESOURCE_ID);
            cache.getNextDelay(Phase.WAITING_FOR_DEPENDS, RESOURCE_ID);
        }

        assertEquals(1, cache.getNextDelay(Phase.UPDATED_PHASE, RESOURCE_ID));
        assertEquals(2, cache.getNextDelay(Phase.BACKING_OFF, RESOURCE_ID));
        assertEquals(2, cache.getNextDelay(Phase.WAITING_FOR_DEPENDS, RESOURCE_ID));
    }

    @Test
    void resetClearsBothCounters() {
        RetryResourceCache cache = new RetryResourceCache();

        for (int i = 0; i < 5; i++) {
            cache.getNextDelay(Phase.BACKING_OFF, RESOURCE_ID);
            cache.getNextDelay(Phase.WAITING_FOR_DEPENDS, RESOURCE_ID);
        }
        cache.reset(RESOURCE_ID);

        assertEquals(1, cache.getNextDelay(Phase.BACKING_OFF, RESOURCE_ID));
        assertEquals(1, cache.getNextDelay(Phase.WAITING_FOR_DEPENDS, RESOURCE_ID));
    }

    @Test
    void countersAreKeptPerResource() {
        RetryResourceCache cache = new RetryResourceCache();
        ResourceID other = new ResourceID("other-declaration", "my-namespace");

        for (int i = 0; i < 5; i++) {
            cache.getNextDelay(Phase.WAITING_FOR_DEPENDS, RESOURCE_ID);
        }

        assertEquals(1, cache.getNextDelay(Phase.WAITING_FOR_DEPENDS, other));
    }

    @Test
    void retryLimitIsNotReachedWhileBudgetRemains() {
        RetryResourceCache cache = new RetryResourceCache();

        cache.getNextDelay(Phase.UPDATING, RESOURCE_ID);
        for (int i = 0; i < CR_RETRY_MAX_ATTEMPTS - 1; i++) {
            cache.getNextDelay(Phase.BACKING_OFF, RESOURCE_ID);
            assertFalse(cache.isRetryLimitReached(RESOURCE_ID));
        }
    }

    @Test
    void retryLimitIsReachedOnceBudgetIsSpent() {
        RetryResourceCache cache = new RetryResourceCache();

        cache.getNextDelay(Phase.UPDATING, RESOURCE_ID);
        for (int i = 0; i < CR_RETRY_MAX_ATTEMPTS; i++) {
            cache.getNextDelay(Phase.BACKING_OFF, RESOURCE_ID);
        }

        assertTrue(cache.isRetryLimitReached(RESOURCE_ID));
    }

    @Test
    void waitingForDependenciesDoesNotSpendTheRetryBudget() {
        RetryResourceCache cache = new RetryResourceCache();

        cache.getNextDelay(Phase.UPDATING, RESOURCE_ID);
        for (int i = 0; i < CR_RETRY_MAX_ATTEMPTS * 2; i++) {
            cache.getNextDelay(Phase.WAITING_FOR_DEPENDS, RESOURCE_ID);
        }

        assertFalse(cache.isRetryLimitReached(RESOURCE_ID));
    }

    @Test
    void resetGivesTheRetryBudgetBack() {
        RetryResourceCache cache = new RetryResourceCache();

        cache.getNextDelay(Phase.UPDATING, RESOURCE_ID);
        for (int i = 0; i < CR_RETRY_MAX_ATTEMPTS; i++) {
            cache.getNextDelay(Phase.BACKING_OFF, RESOURCE_ID);
        }
        cache.reset(RESOURCE_ID);

        assertFalse(cache.isRetryLimitReached(RESOURCE_ID));
    }

    @Test
    void anUntrackedResourceHasNotReachedTheRetryLimit() {
        RetryResourceCache cache = new RetryResourceCache();

        assertFalse(cache.isRetryLimitReached(RESOURCE_ID));
    }
}
