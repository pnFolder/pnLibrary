package ru.privatenull.pnlibrary.core.runtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RuntimeShutdownTest {
    @Test
    fun `runs every action in declaration order despite failures`() {
        val calls = mutableListOf<String>()
        val shutdown = RuntimeShutdown().apply {
            add { calls += "first" }
            add {
                calls += "failing"
                error("expected")
            }
            add { calls += "last" }
        }

        shutdown.run()

        assertEquals(listOf("first", "failing", "last"), calls)
    }
}
