package org.pnfolder.pnlibrary.api.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PnLoggerTest {
    private static final class Line {
        final LogLevel level;
        final Throwable error;
        final Map<String, ?> context;

        Line(LogLevel level, Throwable error, Map<String, ?> context) {
            this.level = level;
            this.error = error;
            this.context = context;
        }
    }

    private static final class Recording implements PnLogger {
        final List<Line> lines = new ArrayList<>();

        @Override
        public void log(LogLevel level, String message, Throwable error, Map<String, ?> context) {
            lines.add(new Line(level, error, context));
        }
    }

    private static Map<String, Object> mapOf(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            map.put((String) pairs[index], pairs[index + 1]);
        }
        return map;
    }

    @Test
    @DisplayName("each level writes at its own level, with the failure when given")
    void eachLevelWritesAtItsOwnLevelWithTheFailureWhenGiven() {
        Recording log = new Recording();
        IllegalStateException failure = new IllegalStateException("boom");

        log.info("a");
        log.warning("b", failure);
        log.error("c");
        log.critical("d", failure);
        log.debug("e");

        List<LogLevel> levels = new ArrayList<>();
        List<Throwable> errors = new ArrayList<>();
        for (Line line : log.lines) {
            levels.add(line.level);
            errors.add(line.error);
        }
        assertEquals(Arrays.asList(LogLevel.INFO, LogLevel.WARNING, LogLevel.ERROR, LogLevel.CRITICAL, LogLevel.DEBUG), levels);
        assertEquals(Arrays.asList(null, failure, null, failure, null), errors);
    }

    @Test
    @DisplayName("with adds facts to every line and the facts add up")
    void withAddsFactsToEveryLineAndTheFactsAddUp() {
        Recording log = new Recording();
        PnLogger shop = log.with("shop", "main");

        shop.with("player", "Steve").warning("refused");
        shop.info("opened");

        assertEquals(mapOf("shop", "main", "player", "Steve"), log.lines.get(0).context);
        assertEquals(mapOf("shop", "main"), log.lines.get(1).context);
    }

    @Test
    @DisplayName("a fact given with the line wins over the one the logger carries")
    void aFactGivenWithTheLineWinsOverTheOneTheLoggerCarries() {
        Recording log = new Recording();

        log.with("id", 1).log(LogLevel.INFO, "x", null, Collections.singletonMap("id", 2));

        assertEquals(Collections.singletonMap("id", 2), log.lines.get(0).context);
    }
}
