package ru.privatenull.pnlibrary.core.runtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RuntimeShutdownTest {
    @Test
    fun `runs every action and preserves all cleanup failures`() {
        val shutdown = RuntimeShutdown()
        val calls = mutableListOf<String>()
        val firstFailure = IllegalStateException("first")
        val secondFailure = IllegalArgumentException("second")

        shutdown.add {
            calls += "first"
            throw firstFailure
        }
        shutdown.add {
            calls += "second"
            throw secondFailure
        }
        shutdown.add { calls += "third" }

        val thrown = assertThrows<IllegalStateException> { shutdown.run() }

        assertEquals(listOf("first", "second", "third"), calls)
        assertSame(firstFailure, thrown)
        assertTrue(thrown.suppressed.single() === secondFailure)
    }
}
