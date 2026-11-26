package org.pnfolder.pnlibrary.core.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.logging.LogLevel;
import org.pnfolder.pnlibrary.spi.platform.PlatformLogging;
import org.pnfolder.pnlibrary.spi.platform.PlatformOwnership;

class DiagnosticCaptureTest {
    private final PlatformOwnership ownership = new PlatformOwnership() {
        @Override
        public @NotNull java.util.Map<String, String> ownerDetails(@NotNull Object owner) {
            return java.util.Collections.singletonMap("name", "plugin");
        }
    };

    private interface Printer {
        void print(Object owner, LogLevel level, String message, Throwable error);
    }

    private static PlatformLogging logging(Printer printer) {
        return new PlatformLogging() {
            @Override
            public void log(@NotNull Object owner, @NotNull LogLevel level, @NotNull String message,
                            @Nullable Throwable error) {
                printer.print(owner, level, message, error);
            }
        };
    }

    @Test
    void nativeEchoesDoNotDuplicateJournalIncidentsOrErrorObservations() {
        DiagnosticLogBuffer buffer = new DiagnosticLogBuffer();
        AtomicInteger journaled = new AtomicInteger();
        AtomicInteger observed = new AtomicInteger();
        AtomicInteger printed = new AtomicInteger();
        DiagnosticCapture[] capture = new DiagnosticCapture[1];
        PlatformLogging nativeLogging = logging((owner, level, message, error) -> {
            printed.incrementAndGet();
            capture[0].observe(owner, level, message, error);
        });
        capture[0] = new DiagnosticCapture(ownership, nativeLogging, buffer,
            (o, l, m, e, c) -> journaled.incrementAndGet(), (o, m, e) -> observed.incrementAndGet());
        IllegalStateException error = new IllegalStateException("failure");
        for (int i = 0; i < 2; i++) {
            capture[0].log(this, LogLevel.ERROR, "command failed", error);
        }
        assertEquals(1, printed.get());
        assertEquals(2, journaled.get());
        assertEquals(2, observed.get());
        assertEquals(2L, buffer.summary().get("occurrenceCount"));

        capture[0].observeWithoutJournal(this, LogLevel.ERROR, "foreign failure", error);
        assertEquals(1, printed.get());
        assertEquals(2, journaled.get());
        assertEquals(3, observed.get());
        assertEquals(3L, buffer.summary().get("occurrenceCount"));
    }

    @Test
    void aLineWrittenWithoutRecordReachesTheConsoleOnly() {
        DiagnosticLogBuffer buffer = new DiagnosticLogBuffer();
        AtomicInteger journaled = new AtomicInteger();
        AtomicInteger observed = new AtomicInteger();
        AtomicInteger printed = new AtomicInteger();
        DiagnosticCapture capture = new DiagnosticCapture(ownership,
            logging((o, l, m, e) -> printed.incrementAndGet()), buffer,
            (o, l, m, e, c) -> journaled.incrementAndGet(), (o, m, e) -> observed.incrementAndGet());
        IllegalStateException error = new IllegalStateException("failure");

        capture.writeUnrecorded(this, LogLevel.ERROR, "noisy but harmless", error);
        assertEquals(1, printed.get());
        assertEquals(0, journaled.get());
        assertEquals(0, observed.get());
        assertEquals(0L, buffer.summary().get("occurrenceCount"));

        capture.write(this, LogLevel.ERROR, "critical", error);
        assertEquals(2, printed.get());
        assertEquals(1, journaled.get());
        assertEquals(1, observed.get());
        assertEquals(1L, buffer.summary().get("occurrenceCount"));
    }

    @Test
    void aDebugLineIsKeptInTheBlackBoxAndNeverPrinted() {
        AtomicInteger journaled = new AtomicInteger();
        AtomicInteger printed = new AtomicInteger();
        DiagnosticCapture capture = new DiagnosticCapture(ownership,
            logging((o, l, m, e) -> printed.incrementAndGet()), new DiagnosticLogBuffer(),
            (o, l, m, e, c) -> journaled.incrementAndGet());

        capture.write(this, LogLevel.DEBUG, "detail", null);
        capture.writeUnrecorded(this, LogLevel.DEBUG, "skipped detail", null);

        assertEquals(0, printed.get());
        assertEquals(1, journaled.get());
    }

    @Test
    void anEchoDeliveredOnAnotherThreadIsIgnoredButUnrelatedLinesStillCount() throws Exception {
        AtomicInteger journaled = new AtomicInteger();
        DiagnosticCapture[] capture = new DiagnosticCapture[1];
        PlatformLogging nativeLogging = logging((owner, level, message, error) -> runOnThread(
            () -> capture[0].observe(owner, level, message, error)));
        capture[0] = new DiagnosticCapture(ownership, nativeLogging, new DiagnosticLogBuffer(),
            (o, l, m, e, c) -> journaled.incrementAndGet());
        capture[0].log(this, LogLevel.WARNING, "disk is slow", null);
        assertEquals(1, journaled.get());

        runOnThread(() -> capture[0].observe(this, LogLevel.WARNING, "someone else's warning", null));
        assertEquals(2, journaled.get());
    }

    private static void runOnThread(Runnable action) {
        Thread thread = new Thread(action);
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException error) {
            throw new IllegalStateException(error);
        }
    }

    @Test
    void brokenDiagnosticSinksCannotPreventConsoleOutput() {
        AtomicInteger printed = new AtomicInteger();
        DiagnosticCapture capture = new DiagnosticCapture(ownership,
            logging((o, l, m, e) -> printed.incrementAndGet()), null,
            (o, l, m, e, c) -> { throw new IllegalStateException("journal"); },
            (o, m, e) -> { throw new IllegalStateException("metrics"); });
        capture.log(this, LogLevel.ERROR, "failure", new IllegalStateException());
        // The line itself plus one report for each of the two broken stages.
        assertEquals(3, printed.get());
    }

    @Test
    void aStageThatFailsIsReportedOnceToTheConsoleAndDoesNotStopTheOtherStages() {
        DiagnosticLogBuffer buffer = new DiagnosticLogBuffer();
        List<Object[]> printed = new ArrayList<>();
        AtomicInteger reports = new AtomicInteger();
        DiagnosticCapture capture = new DiagnosticCapture(ownership,
            logging((o, l, m, e) -> printed.add(new Object[] {l, e})), buffer,
            (o, l, m, e, c) -> { throw new IllegalStateException("journal broke"); },
            (o, m, e) -> {
                reports.incrementAndGet();
                throw new IllegalStateException("report broke");
            });

        for (int i = 0; i < 2; i++) {
            capture.log(this, LogLevel.ERROR, "command failed", new IllegalArgumentException("boom"));
        }

        assertEquals(2, reports.get());
        assertEquals(2L, buffer.summary().get("occurrenceCount"));
        List<String> failures = new ArrayList<>();
        for (Object[] line : printed) {
            if (line[0] == LogLevel.ERROR && line[1] instanceof IllegalStateException) {
                failures.add(((Throwable) line[1]).getMessage());
            }
        }
        assertEquals(java.util.Arrays.asList("journal broke", "report broke"), failures);
    }

    @Test
    void anErrorLoggedAsTextOnlyStillReachesTheErrorReport() {
        List<Throwable> reported = new ArrayList<>();
        DiagnosticCapture capture = new DiagnosticCapture(ownership, logging((o, l, m, e) -> { }), null, null,
            (o, m, e) -> reported.add(e));

        capture.log(this, LogLevel.ERROR, "text only", null);
        capture.log(this, LogLevel.WARNING, "just a warning", null);

        assertEquals(1, reported.size());
        assertEquals("text only", reported.get(0).getMessage());
    }
}
