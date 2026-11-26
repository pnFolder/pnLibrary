package org.pnfolder.pnlibrary.core.metrics;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.metrics.error.ErrorReporter;
import org.pnfolder.pnlibrary.api.metrics.error.TelemetryError;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ErrorPipelineRedactionTest {
    /** The four replacements exactly as they were written inline before the patterns became constants. */
    private static String legacy(String value) {
        return value
                .replaceAll("(?i)(Bearer\\s+)[A-Za-z0-9._~+/=-]+", "$1[redacted]")
                .replaceAll("(?i)([?&](?:api_?key|token|secret)=)[^&\\s]+", "$1[redacted]")
                .replaceAll("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}", "[uuid]")
                .replaceAll("(?i)(/home/|/Users/|C:\\\\Users\\\\)[^/\\\\\\s]+", "$1[user]");
    }

    @Test
    void sanitizedTextEqualsTheOldInlineRegexResultOnAFixedCorpus() {
        List<String> corpus = Arrays.asList(
                "Bearer abc.def-123 failed",
                "GET /x?api_key=SECRET&token=t0k&other=1",
                "player " + new UUID(1, 2) + " and " + new UUID(3, 4).toString().toUpperCase(Locale.ROOT),
                "C:\\Users\\alice\\plugin\\Main.kt:10",
                "/home/bob/server/plugins/a.jar and /Users/carol/x",
                "nothing to hide",
                "");
        List<TelemetryError> received = new ArrayList<>();
        ErrorPipeline pipeline = new ErrorPipeline(new ErrorReporter() {
            @Override
            public void capture(@NotNull TelemetryError error) {
                received.add(error);
            }

            @Override
            public void close() {
            }
        });

        for (int index = 0; index < corpus.size(); index++) {
            String text = corpus.get(index);
            pipeline.capture(new TelemetryError("T" + index, text, Collections.singletonList(text), true, null,
                    Collections.singletonMap("k", text)));
        }

        assertEquals(corpus.size(), received.size());
        for (int index = 0; index < corpus.size(); index++) {
            String expected = legacy(corpus.get(index));
            assertEquals(expected, received.get(index).getMessage());
            assertEquals(Collections.singletonList(expected), received.get(index).getStackTrace());
            assertEquals(expected, received.get(index).getAttributes().get("k"));
        }
    }
}
