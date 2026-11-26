package org.pnfolder.pnlibrary.core.diagnostics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.core.diagnostics.redaction.DiagnosticRedactor;

class DiagnosticRedactorTest {
    private final DiagnosticRedactor redactor = new DiagnosticRedactor();

    @Test
    void redactsPasswordsTokensAndSecrets() {
        String redacted = redactor.redact("db_password: super_secret_123!");

        assertTrue(redacted.contains("[REDACTED: credential line]"));
        assertFalse(redacted.contains("super_secret_123!"));
    }

    @Test
    void redactsUrlsEmailsAndUuids() {
        String input = "Connect to https://secret.db.server.com:3306 or contact admin@example.com for UUID "
            + "123e4567-e89b-12d3-a456-426614174000";

        String redacted = redactor.redact(input);

        assertTrue(redacted.contains("[REDACTED: URL]"));
        assertTrue(redacted.contains("[REDACTED: email]"));
        assertTrue(redacted.contains("[REDACTED: UUID]"));
    }
}
