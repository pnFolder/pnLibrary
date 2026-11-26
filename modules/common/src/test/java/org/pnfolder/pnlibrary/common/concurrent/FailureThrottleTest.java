package org.pnfolder.pnlibrary.common.concurrent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FailureThrottleTest {
    @Test
    void letsTheFirstFailureThroughAndHoldsRepeatsUntilTheIntervalPasses() {
        long[] now = {1_000L};
        FailureThrottle throttle = new FailureThrottle(60_000, () -> now[0]);

        assertTrue(throttle.shouldReport("a"));
        assertFalse(throttle.shouldReport("a"));
        assertTrue(throttle.shouldReport("b"));

        now[0] += 60_000;
        assertTrue(throttle.shouldReport("a"));
    }
}
