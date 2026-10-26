package org.pnfolder.pnlibrary.common.lang

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class FailuresTest {
    @Test
    fun `a failing JVM is rethrown`() {
        assertThrows(OutOfMemoryError::class.java) { Failures.rethrowIfFatal(OutOfMemoryError()) }
        assertThrows(StackOverflowError::class.java) { Failures.rethrowIfFatal(StackOverflowError()) }
    }

    @Test
    fun `linkage errors and ordinary failures are left to the caller`() {
        assertDoesNotThrow { Failures.rethrowIfFatal(NoClassDefFoundError("missing bridge")) }
        assertDoesNotThrow { Failures.rethrowIfFatal(NoSuchMethodError("old server")) }
        assertDoesNotThrow { Failures.rethrowIfFatal(IllegalStateException("boom")) }
    }
}

class RunCatchingNonFatalTest {
    @Test
    fun `ordinary failures and results are returned`() {
        org.junit.jupiter.api.Assertions.assertEquals(7, runCatchingNonFatal { 7 }.getOrNull())
        org.junit.jupiter.api.Assertions.assertTrue(runCatchingNonFatal { error("boom") }.isFailure)
    }

    @Test
    fun `a failing JVM escapes`() {
        assertThrows(OutOfMemoryError::class.java) { runCatchingNonFatal { throw OutOfMemoryError() } }
    }

    @Test
    fun `an interrupt is recorded on the thread`() {
        try {
            org.junit.jupiter.api.Assertions.assertTrue(runCatchingNonFatal { throw InterruptedException() }.isFailure)
            org.junit.jupiter.api.Assertions.assertTrue(Thread.interrupted())
        } finally {
            Thread.interrupted()
        }
    }
}
