package org.pnfolder.pnlibrary.core.metrics;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.metrics.error.ErrorReporter;
import org.pnfolder.pnlibrary.api.metrics.error.TelemetryError;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ErrorPipelineTest {
    @Test
    void sanitizesAndDeduplicatesErrorsBeforeForwarding() {
        List<TelemetryError> received = new ArrayList<>();
        ErrorReporter delegate = new ErrorReporter() {
            @Override
            public void capture(@NotNull TelemetryError error) {
                received.add(error);
            }

            @Override
            public void close() {
            }
        };
        ErrorPipeline pipeline = new ErrorPipeline(delegate);
        TelemetryError error = new TelemetryError("java.lang.IllegalStateException", "Bearer secret-token?token=abc",
                Collections.singletonList("C:\\Users\\alice\\plugin\\Main.kt:10"), false);

        pipeline.capture(error);
        pipeline.capture(error);

        assertEquals(1, received.size());
        assertEquals("Bearer [redacted]?token=[redacted]", received.get(0).getMessage());
        assertEquals("C:\\Users\\[user]\\plugin\\Main.kt:10", received.get(0).getStackTrace().get(0));
    }

    @Test
    void aFailingTrackerIsReportedOnceAndDoesNotReachTheCaller() {
        List<Throwable> failures = new ArrayList<>();
        ErrorReporter delegate = new ErrorReporter() {
            @Override
            public void capture(@NotNull TelemetryError error) {
                throw new IllegalStateException("tracker is down");
            }

            @Override
            public void close() {
            }
        };
        ErrorPipeline pipeline = new ErrorPipeline(delegate, failures::add);

        for (int index = 0; index < 2; index++) {
            pipeline.capture(new TelemetryError("a.Error", "failure " + index, Collections.emptyList(), true));
        }

        List<String> messages = new ArrayList<>();
        for (Throwable failure : failures) {
            messages.add(failure.getMessage());
        }
        assertEquals(Arrays.asList("tracker is down"), messages);
        assertEquals(2L, pipeline.diagnosticSnapshot().get("forwardingFailures"));
    }
}
