package org.pnfolder.pnlibrary.core.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.logging.LogLevel;
import org.pnfolder.pnlibrary.core.diagnostics.redaction.DiagnosticRedactor;
import org.pnfolder.pnlibrary.core.diagnostics.redaction.DiagnosticThrowables;

class DiagnosticLogBufferRepeatTest {
    private final TestPlatform platform = new TestPlatform("shop");

    private static final class Record {
        final LogLevel level;
        final String message;
        final Throwable error;

        Record(LogLevel level, String message, Throwable error) {
            this.level = level;
            this.message = message;
            this.error = error;
        }
    }

    private static final class Counting {
        final AtomicInteger redacts = new AtomicInteger();
        final AtomicInteger fingerprints = new AtomicInteger();
        private final DiagnosticRedactor redactor = new DiagnosticRedactor();

        DiagnosticLogBuffer buffer() {
            return new DiagnosticLogBuffer(100, text -> {
                redacts.incrementAndGet();
                return redactor.redact(text);
            }, (context, message, error) -> {
                fingerprints.incrementAndGet();
                return DiagnosticThrowables.fingerprint(error, context, message);
            });
        }
    }

    private static Throwable cause(String message, String method) {
        IllegalStateException error = new IllegalStateException(message);
        error.setStackTrace(new StackTraceElement[] {new StackTraceElement("ru.shop.Menu", method, "Menu.kt", 10)});
        return error;
    }

    /** What the buffer must report: every record redacted and fingerprinted on its own, as the old code did. */
    private static Map<String, Object[]> expected(List<Record> records) {
        DiagnosticRedactor redactor = new DiagnosticRedactor();
        Map<String, Object[]> result = new LinkedHashMap<String, Object[]>();
        for (Record record : records) {
            String safe = cut(redactor.redact(record.message), 4_096);
            String id = DiagnosticThrowables.fingerprint(record.error,
                Arrays.asList(cut(redactor.redact("shop"), 96), record.level.name()), safe);
            Object[] old = result.get(id);
            result.put(id, new Object[] {old == null ? safe : old[0], old == null ? 1L : (Long) old[1] + 1});
        }
        return result;
    }

    private static String cut(String text, int limit) {
        return text.length() <= limit ? text : text.substring(0, limit);
    }

    @Test
    void tenThousandIdenticalRecordsRedactAndFingerprintOnce() {
        Counting counting = new Counting();
        DiagnosticLogBuffer buffer = counting.buffer();
        Throwable error = cause("database unavailable", "load");

        for (int i = 0; i < 10_000; i++) {
            buffer.record(platform, this, LogLevel.ERROR, "Load failed", error);
        }

        assertEquals(2, counting.redacts.get());
        assertEquals(1, counting.fingerprints.get());
        assertEquals(10_000L, ((Map<?, ?>) buffer.snapshot().get(0).get("occurrences")).get("count"));
    }

    @Test
    void repeatsWithDifferentIdsAndNumbersStillFoldIntoTheSameIncidentsAsBefore() {
        List<Record> records = new ArrayList<Record>();
        for (int index = 0; index < 300; index++) {
            records.add(new Record(LogLevel.WARNING, "Player " + new UUID(index, 7) + " lost " + (10_000 + index) + " coins", null));
            records.add(new Record(LogLevel.ERROR, "Load failed", cause("db down", "load")));
            records.add(new Record(LogLevel.ERROR, "Load failed", cause("db down " + (100_000 + index), "load")));
            records.add(new Record(LogLevel.ERROR, "Load failed", cause("db down", "save")));
            records.add(new Record(LogLevel.WARNING, "token=secret" + index + " at https://example.invalid/" + index, null));
            records.add(new Record(LogLevel.ERROR, "Other", null));
        }
        Counting counting = new Counting();
        DiagnosticLogBuffer buffer = counting.buffer();
        for (Record record : records) {
            buffer.record(platform, this, record.level, record.message, record.error);
        }

        Map<String, Object[]> want = expected(records);
        Map<String, Object[]> got = new LinkedHashMap<String, Object[]>();
        for (Map<String, Object> entry : buffer.snapshot()) {
            got.put((String) entry.get("incidentId"), new Object[] {entry.get("message"),
                ((Map<?, ?>) entry.get("occurrences")).get("count")});
        }
        assertEquals(want.keySet(), got.keySet());
        for (String id : want.keySet()) {
            assertTrue(Arrays.equals(want.get(id), got.get(id)), id);
        }
        assertTrue(want.size() >= 5, "corpus must produce several incidents: " + want.size());
        assertTrue(counting.fingerprints.get() <= records.size() / 2 + 10, "fingerprints: " + counting.fingerprints.get());
    }

    private static String normalize(String value) {
        return Pattern.compile("\\s+").matcher(Pattern.compile("\\b\\d{4,}\\b").matcher(
            Pattern.compile("(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b").matcher(value)
                .replaceAll("<uuid>")).replaceAll("<number>")).replaceAll(" ").trim();
    }

    @Test
    void fingerprintHexMatchesTheOldFormatting() throws Exception {
        for (int i = 0; i < 200; i++) {
            Throwable error = i % 2 == 0 ? cause("m" + i, "f" + i) : null;
            List<String> context = Arrays.asList("plugin" + i, "ERROR");
            Throwable significant = DiagnosticThrowables.significantCause(error);
            StackTraceElement frame = DiagnosticThrowables.originFrame(error);
            List<String> parts = new ArrayList<String>(context);
            parts.add(normalize("message " + i));
            parts.add(significant == null ? "" : significant.getClass().getName());
            parts.add(normalize(significant == null || significant.getMessage() == null ? "" : significant.getMessage()));
            parts.add(frame == null ? "" : frame.getClassName());
            parts.add(frame == null ? "" : frame.getMethodName());
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(String.join("\u0000", parts).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int b = 0; b < 8; b++) {
                hex.append(String.format("%02x", digest[b]));
            }
            assertEquals(hex.toString(), DiagnosticThrowables.fingerprint(error, context, "message " + i));
        }
    }

    @Test
    void memoryOfRawKeysStaysBoundedWhenEveryMessageIsUnique() {
        DiagnosticLogBuffer buffer = new DiagnosticLogBuffer(50);
        for (int i = 0; i < 20_000; i++) {
            buffer.record(platform, this, LogLevel.WARNING, "unique message number-" + i + " x", null);
        }
        assertEquals(50, buffer.snapshot().size());
    }
}
