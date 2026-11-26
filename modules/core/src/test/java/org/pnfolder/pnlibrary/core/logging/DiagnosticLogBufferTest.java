package org.pnfolder.pnlibrary.core.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.logging.LogLevel;

class DiagnosticLogBufferTest {
    @Test
    void keepsOnlyWarningAndErrorEntriesAndRedactsSecrets() {
        DiagnosticLogBuffer buffer = new DiagnosticLogBuffer(10);
        TestPlatform platform = new TestPlatform("test-plugin");
        buffer.record(platform, this, LogLevel.INFO, "ready", null);
        buffer.record(platform, this, LogLevel.WARNING, "token=https://example.invalid/private", null);

        List<Map<String, Object>> entries = buffer.snapshot();
        assertEquals(1, entries.size());
        assertEquals("WARNING", entries.get(0).get("level"));
        String message = entries.get(0).get("message").toString();
        assertFalse(message.contains("example.invalid"));
        assertTrue(message.contains("REDACTED"));
    }

    @Test
    void incidentsLastSeenBeforeTheCutoffExpireAndLaterOnesStay() throws Exception {
        DiagnosticLogBuffer buffer = new DiagnosticLogBuffer(10);
        TestPlatform platform = new TestPlatform("test-plugin");
        buffer.record(platform, this, LogLevel.ERROR, "old failure", null);
        long cutoff = System.currentTimeMillis() + 1;
        Thread.sleep(5);
        buffer.record(platform, this, LogLevel.ERROR, "new failure", null);

        assertEquals(1, buffer.expireOlderThan(cutoff));
        List<Object> messages = new ArrayList<>();
        for (Map<String, Object> entry : buffer.snapshot()) {
            messages.add(entry.get("message"));
        }
        assertEquals(java.util.Collections.singletonList("new failure"), messages);
        assertEquals(0, buffer.expireOlderThan(cutoff));
    }

    @Test
    void aggregatesRepeatsWithExactTimestampsAndKeepsTheCompleteThrowable() {
        DiagnosticLogBuffer buffer = new DiagnosticLogBuffer(10);
        TestPlatform platform = new TestPlatform("test-plugin");
        NoSuchMethodError cause = new NoSuchMethodError("PlayerPointsAPI.getCurrencyName(int)");
        cause.setStackTrace(new StackTraceElement[] {
            new StackTraceElement("ru.privatenull.pncases.Menu", "open", "Menu.kt", 91),
            new StackTraceElement("org.bukkit.plugin.EventExecutor", "execute", "EventExecutor.java", 77)});
        CompletionException error = new CompletionException(cause);
        error.setStackTrace(new StackTraceElement[] {
            new StackTraceElement("java.util.concurrent.CompletableFuture", "encodeThrowable", "CompletableFuture.java", 315),
            new StackTraceElement("ru.privatenull.pncases.AnvilGUI", "click", "AnvilGUI.java", 362)});

        DiagnosticLogBuffer.CaptureResult first = buffer.record(platform, this, LogLevel.ERROR, "Could not pass event", error);
        DiagnosticLogBuffer.CaptureResult second = buffer.record(platform, this, LogLevel.ERROR, "Could not pass event", error);

        assertTrue(first != null && first.isEmitOriginal());
        assertFalse(second != null && second.isEmitOriginal());
        Map<String, Object> incident = buffer.snapshot().get(0);
        assertEquals(1, buffer.snapshot().size());
        Map<?, ?> occurrences = (Map<?, ?>) incident.get("occurrences");
        assertEquals(2L, occurrences.get("count"));
        assertEquals(2, ((List<?>) occurrences.get("timeline")).size());
        assertEquals("Menu.kt", ((Map<?, ?>) incident.get("origin")).get("file"));
        String fullError = incident.get("fullError").toString();
        assertTrue(fullError.contains("CompletionException"));
        assertTrue(fullError.contains("NoSuchMethodError"));
        assertTrue(fullError.contains("CompletableFuture.java:315"));
        assertTrue(fullError.contains("Menu.kt:91"));
        assertTrue(fullError.contains("Caused by:"));
        String consoleBlock = incident.get("consoleBlock").toString();
        assertTrue(consoleBlock.contains("ERROR] Could not pass event"));
        assertTrue(consoleBlock.contains("NoSuchMethodError"));
    }

    @Test
    void loggerPrintsTheFullRepeatedErrorOnlyOnce() {
        TestPlatform platform = new TestPlatform("test-plugin");
        org.pnfolder.pnlibrary.api.logging.PnLogger logger = new ModuleLogging(platform, platform, platform, platform,
            new DiagnosticLogBuffer(10)).logger(this, "test");
        IllegalStateException error = new IllegalStateException("database unavailable");

        logger.error("Load failed", error);
        logger.error("Load failed", error);

        assertEquals(1, platform.logged.size());
        assertEquals(error, platform.logged.get(0)[1]);
    }
}
