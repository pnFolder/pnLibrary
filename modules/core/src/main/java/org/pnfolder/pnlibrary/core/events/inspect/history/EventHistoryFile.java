package org.pnfolder.pnlibrary.core.events.inspect.history;

import org.pnfolder.pnlibrary.common.lang.Failures;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * The text format of the history file: a version line, then one {@code period<TAB>event<TAB>calls<TAB>denied} line per
 * day and event, and the same for hours with {@code yyyy-MM-ddTHH:00} as the period. Lines of another shape are ignored.
 */
final class EventHistoryFile {
    /** The first line of the history file, which names its format version. */
    static final String HEADER = "# pnLibrary event history v1";

    private EventHistoryFile() {
    }

    /** Receives the counts read from a file. */
    interface Sink {
        void day(LocalDate date, String event, long calls, long denied);

        void hour(LocalDateTime hour, String event, long calls, long denied);
    }

    static void read(Path source, Sink sink) throws IOException {
        if (!Files.isRegularFile(source)) {
            return;
        }
        for (String line : Files.readAllLines(source)) {
            readLine(line, sink);
        }
    }

    private static void readLine(String line, Sink sink) {
        String[] parts = line.split("\t", -1);
        if (parts.length != 4) {
            return;
        }
        Long calls = parseLong(parts[2]);
        Long denied = parseLong(parts[3]);
        if (calls == null || denied == null) {
            return;
        }
        if (parts[0].indexOf('T') >= 0) {
            LocalDateTime hour = attempt(() -> LocalDateTime.parse(parts[0]));
            if (hour != null) {
                sink.hour(hour, parts[1], calls, denied);
            }
        } else {
            LocalDate date = attempt(() -> LocalDate.parse(parts[0]));
            if (date != null) {
                sink.day(date, parts[1], calls, denied);
            }
        }
    }

    static String render(Map<LocalDate, ? extends Map<String, EventCounts>> days,
                         Map<LocalDateTime, ? extends Map<String, EventCounts>> hours) {
        StringBuilder text = new StringBuilder(HEADER).append('\n');
        for (Map.Entry<LocalDate, ? extends Map<String, EventCounts>> day : days.entrySet()) {
            appendLines(text, day.getKey(), day.getValue());
        }
        for (Map.Entry<LocalDateTime, ? extends Map<String, EventCounts>> hour : hours.entrySet()) {
            appendLines(text, hour.getKey(), hour.getValue());
        }
        return text.toString();
    }

    private static void appendLines(StringBuilder text, Object period, Map<String, EventCounts> events) {
        for (Map.Entry<String, EventCounts> event : events.entrySet()) {
            text.append(period).append('\t').append(event.getKey()).append('\t').append(event.getValue().getCalls())
                    .append('\t').append(event.getValue().getDenied()).append('\n');
        }
    }

    private static Long parseLong(String text) {
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    private static <T> T attempt(Callable<T> parse) {
        return Failures.runCatchingNonFatal(parse).getOrNull();
    }
}
