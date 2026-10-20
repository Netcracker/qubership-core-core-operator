package com.netcracker.core.declarative.client.cache;

import com.netcracker.core.declarative.resources.base.Phase;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import org.junit.jupiter.api.Test;

import static com.netcracker.core.declarative.client.constants.Constants.CR_POLLING_MAX_INTERVAL;
import static com.netcracker.core.declarative.client.constants.Constants.CR_RETRY_MAX_INTERVAL;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void pollingDelayGrowsAndIsCappedSeparately() {
        RetryResourceCache cache = new RetryResourceCache();

        assertEquals(1, cache.getNextDelay(Phase.WAITING_FOR_DEPENDS, RESOURCE_ID));
        assertEquals(2, cache.getNextDelay(Phase.WAITING_FOR_DEPENDS, RESOURCE_ID));
        assertEquals(4, cache.getNextDelay(Phase.WAITING_FOR_DEPENDS, RESOURCE_ID));

        for (int i = 0; i < 20; i++) {
            cache.getNextDelay(Phase.WAITING_FOR_DEPENDS, RESOURCE_ID);
        }
        assertEquals((int) CR_POLLING_MAX_INTERVAL, cache.getNextDelay(Phase.WAITING_FOR_DEPENDS, RESOURCE_ID));
    }

    @Test
    void pollingAttemptsDoNotResetBackingOffDelay() {
        RetryResourceCache cache = new RetryResourceCache();

        cache.getNextDelay(Phase.UPDATING, RESOURCE_ID);
        assertEquals(2, cache.getNextDelay(Phase.BACKING_OFF, RESOURCE_ID));
        assertEquals(4, cache.getNextDelay(Phase.BACKING_OFF, RESOURCE_ID));

        //operation accepted, then a status request fails and the CR is re-applied
        cache.getNextDelay(Phase.WAITING_FOR_DEPENDS, RESOURCE_ID);
        assertEquals(5, cache.getNextDelay(Phase.BACKING_OFF, RESOURCE_ID));
    }

    @Test
    void newApplyAttemptRestartsPolling() {
        RetryResourceCache cache = new RetryResourceCache();

        cache.getNextDelay(Phase.UPDATING, RESOURCE_ID);
        for (int i = 0; i < 20; i++) {
            cache.getNextDelay(Phase.WAITING_FOR_DEPENDS, RESOURCE_ID);
        }
        assertEquals((int) CR_POLLING_MAX_INTERVAL, cache.getNextDelay(Phase.WAITING_FOR_DEPENDS, RESOURCE_ID));

        //status request fails, CR is re-applied and accepted again as a new operation
        assertEquals(2, cache.getNextDelay(Phase.BACKING_OFF, RESOURCE_ID));
        assertEquals(2, cache.getNextDelay(Phase.WAITING_FOR_DEPENDS, RESOURCE_ID));
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
}
