package ru.privatenull.pnlibrary.core.remote

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RemotePolicyFailureInterpreterTest {
    @Test
    fun `replaces compiler internals with a clear message`() {
        assertEquals(
            "Не удалось скомпилировать удалённую policy",
            RemotePolicyFailureInterpreter.message(IllegalStateException("Kotlin compilation failed at line 42")),
        )
    }

    @Test
    fun `keeps only the first useful line of an ordinary failure`() {
        assertEquals(
            "Сервер вернул HTTP 503",
            RemotePolicyFailureInterpreter.message(IllegalStateException("Сервер вернул HTTP 503\nstack details")),
        )
    }
}
