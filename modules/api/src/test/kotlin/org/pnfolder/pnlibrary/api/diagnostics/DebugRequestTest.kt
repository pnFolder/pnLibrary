package org.pnfolder.pnlibrary.api.diagnostics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DebugRequestTest {
    @Test
    fun `a plain request always carries logs and configurations`() {
        val request = DebugRequest.parse(arrayOf("debug"), prefixed = true)

        assertEquals("all", request.target)
        assertTrue(request.logs)
        assertTrue(request.configs)
        assertFalse(request.local)
    }

    @Test
    fun `local only changes where the file goes`() {
        val request = DebugRequest.parse(arrayOf("pnClans", "--local"))

        assertEquals("pnClans", request.target)
        assertTrue(request.local && request.logs && request.configs)
    }

    @Test
    fun `flags for the content no longer exist`() {
        for (flag in listOf("--full", "--logs", "--config", "--no-logs", "--no-config", "--nope")) {
            assertThrows(IllegalArgumentException::class.java, { DebugRequest.parse(arrayOf(flag)) }, flag)
        }
        assertThrows(IllegalArgumentException::class.java) { DebugRequest.parse(arrayOf("--local", "--local")) }
        assertThrows(IllegalArgumentException::class.java) { DebugRequest.parse(arrayOf("a", "b")) }
    }
}
