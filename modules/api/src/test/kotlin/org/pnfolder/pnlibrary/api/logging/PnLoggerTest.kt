package org.pnfolder.pnlibrary.api.logging

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PnLoggerTest {
    private class Line(val level: LogLevel, val message: String, val error: Throwable?, val context: Map<String, Any?>)

    private class Recording : PnLogger {
        val lines = mutableListOf<Line>()
        override fun log(level: LogLevel, message: String, error: Throwable?, context: Map<String, Any?>) {
            lines += Line(level, message, error, context)
        }
    }

    @Test
    fun `each level writes at its own level, with the failure when given`() {
        val log = Recording()
        val failure = IllegalStateException("boom")

        log.info("a")
        log.warning("b", failure)
        log.error("c")
        log.critical("d", failure)
        log.debug("e")

        assertEquals(listOf(LogLevel.INFO, LogLevel.WARNING, LogLevel.ERROR, LogLevel.CRITICAL, LogLevel.DEBUG), log.lines.map { it.level })
        assertEquals(listOf(null, failure, null, failure, null), log.lines.map { it.error })
    }

    @Test
    fun `with adds facts to every line and the facts add up`() {
        val log = Recording()
        val shop = log.with("shop" to "main")

        shop.with("player" to "Steve").warning("refused")
        shop.info("opened")

        assertEquals(mapOf("shop" to "main", "player" to "Steve"), log.lines[0].context)
        assertEquals(mapOf("shop" to "main"), log.lines[1].context)
    }

    @Test
    fun `a fact given with the line wins over the one the logger carries`() {
        val log = Recording()

        log.with("id" to 1).log(LogLevel.INFO, "x", context = mapOf("id" to 2))

        assertEquals(mapOf("id" to 2), log.lines.single().context)
    }
}
