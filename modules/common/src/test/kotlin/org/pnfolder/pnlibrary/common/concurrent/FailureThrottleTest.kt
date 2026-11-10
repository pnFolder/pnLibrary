package org.pnfolder.pnlibrary.common.concurrent

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FailureThrottleTest {
    @Test
    fun `lets the first failure through and holds repeats until the interval passes`() {
        var now = 1_000L
        val throttle = FailureThrottle(60_000) { now }

        assertTrue(throttle.shouldReport("a"))
        assertFalse(throttle.shouldReport("a"))
        assertTrue(throttle.shouldReport("b"))

        now += 60_000
        assertTrue(throttle.shouldReport("a"))
    }
}
