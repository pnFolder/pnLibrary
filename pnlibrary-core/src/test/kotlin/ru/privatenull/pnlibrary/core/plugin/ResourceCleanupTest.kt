package ru.privatenull.pnlibrary.core.plugin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ResourceCleanupTest {
    @Test
    fun `rollback retains primary failure and records cleanup failures`() {
        val primary = IllegalStateException("registration")
        val cleanup = IllegalArgumentException("cleanup")

        ResourceCleanup.suppressInto(primary, { throw cleanup }, {})

        assertEquals(listOf(cleanup), primary.suppressed.toList())
    }

    @Test
    fun `close all executes later steps and combines failures`() {
        val calls = mutableListOf<Int>()
        val first = IllegalStateException("first")
        val second = IllegalArgumentException("second")

        val thrown = assertThrows(IllegalStateException::class.java) {
            ResourceCleanup.closeAll(
                { calls += 1; throw first },
                { calls += 2 },
                { calls += 3; throw second },
            )
        }

        assertSame(first, thrown)
        assertEquals(listOf(1, 2, 3), calls)
        assertEquals(listOf(second), thrown.suppressed.toList())
    }
}
