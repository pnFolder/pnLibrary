package ru.privatenull.pnlibrary.core

import ru.privatenull.pnlibrary.core.diagnostics.DiagnosticRedactor


import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DiagnosticRedactorTest {

    private val redactor = DiagnosticRedactor()

    @Test
    fun `redacts passwords tokens and secrets`() {
        val input = "db_password: super_secret_123!"
        val redacted = redactor.redact(input)
        assertTrue(redacted.contains("[REDACTED: credential line]"))
        assertFalse(redacted.contains("super_secret_123!"))
    }

    @Test
    fun `redacts URLs emails and UUIDs`() {
        val input = "Connect to https://secret.db.server.com:3306 or contact admin@example.com for UUID 123e4567-e89b-12d3-a456-426614174000"
        val redacted = redactor.redact(input)
        assertTrue(redacted.contains("[REDACTED: URL]"))
        assertTrue(redacted.contains("[REDACTED: email]"))
        assertTrue(redacted.contains("[REDACTED: UUID]"))
    }
}
