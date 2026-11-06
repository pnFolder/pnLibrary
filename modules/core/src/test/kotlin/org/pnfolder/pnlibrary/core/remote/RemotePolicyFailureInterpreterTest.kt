package org.pnfolder.pnlibrary.core.remote

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RemotePolicyFailureInterpreterTest {
    @Test
    fun `replaces compiler internals with a clear message`() {
        assertEquals(
            "Не удалось скомпилировать политику",
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

    @Test
    fun `a missing network says that the check needs internet, even deep in the cause chain`() {
        val offline = IllegalStateException("load failed", java.io.IOException("io", java.net.UnknownHostException("raw.githubusercontent.com")))
        assertEquals(true, RemotePolicyFailureInterpreter.isNetwork(offline))
        assertEquals("Нет доступа к интернету: политику не удалось скачать", RemotePolicyFailureInterpreter.message(offline))
        assertEquals(false, RemotePolicyFailureInterpreter.isNetwork(IllegalStateException("Сервер вернул HTTP 503")))
    }

    @Test
    fun `a server without javac is told to run on a JDK`() {
        assertEquals(
            "На сервере нет компилятора Java (это JRE), а запасной компилятор скачать не удалось",
            RemotePolicyFailureInterpreter.message(IllegalStateException("Java compiler is unavailable; run the server with a JDK")),
        )
    }

    @Test
    fun `a missing JDK and a missing network are server setup, a broken policy is not`() {
        assertEquals(true, RemotePolicyFailureInterpreter.isEnvironment(IllegalStateException("Java compiler is unavailable; run the server with a JDK")))
        assertEquals(true, RemotePolicyFailureInterpreter.isEnvironment(java.net.UnknownHostException("raw.githubusercontent.com")))
        assertEquals(false, RemotePolicyFailureInterpreter.isEnvironment(IllegalArgumentException("Remote policy compilation failed")))
    }
}
