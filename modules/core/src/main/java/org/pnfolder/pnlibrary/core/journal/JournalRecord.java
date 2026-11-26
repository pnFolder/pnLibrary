package org.pnfolder.pnlibrary.core.journal;

import org.jetbrains.annotations.NotNull;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/** One journal entry: when it happened, how severe it was, who wrote it and what it says. */
public final class JournalRecord {
    /** Type of the hourly statistics records; they are not events, so reads without a type skip them. */
    public static final String STATS_TYPE = "STATS";

    /** Upper bound for stored text; longer messages are cut so one record always fits a block. */
    public static final int MAX_MESSAGE_CHARS = 8_000;

    /** Upper bound for every detail key and value. */
    public static final int MAX_DETAIL_CHARS = 1_000;

    /** Detail key that holds the full stack trace of an error, including its causes. */
    public static final String STACKTRACE = "stacktrace";

    /** Upper bound for the stack trace; at three bytes per character it still fits one UTF string. */
    public static final int MAX_STACK_CHARS = 20_000;

    private final long timeMillis;
    private final JournalLevel level;
    private final String source;
    private final String type;
    private final String message;
    private final Map<String, String> details;
    // Benign race: a String is immutable, so two threads that compute it at once store equal values.
    private String cachedFingerprint;

    /**
     * Creates a record.
     *
     * @param timeMillis wall-clock time of the event in epoch milliseconds
     * @param level severity, which also selects the file set and retention
     * @param source plugin or component that produced the record, for example {@code pnlibrary} or {@code shop}
     * @param type kind of record: {@code LOG}, {@code ERROR}, {@code NOTE}, or {@code COMPLAINT}
     * @param message short human-readable text, truncated to {@link #MAX_MESSAGE_CHARS} when stored
     * @param details extra key-value facts such as a player name or a plugin version
     */
    public JournalRecord(long timeMillis, @NotNull JournalLevel level, @NotNull String source, @NotNull String type,
                         @NotNull String message, @NotNull Map<String, String> details) {
        this.timeMillis = timeMillis;
        this.level = Objects.requireNonNull(level, "level");
        this.source = Objects.requireNonNull(source, "source");
        this.type = Objects.requireNonNull(type, "type");
        this.message = Objects.requireNonNull(message, "message");
        this.details = Objects.requireNonNull(details, "details");
    }

    public JournalRecord(long timeMillis, @NotNull JournalLevel level, @NotNull String source, @NotNull String type,
                         @NotNull String message) {
        this(timeMillis, level, source, type, message, Collections.emptyMap());
    }

    public long getTimeMillis() {
        return timeMillis;
    }

    public @NotNull JournalLevel getLevel() {
        return level;
    }

    public @NotNull String getSource() {
        return source;
    }

    public @NotNull String getType() {
        return type;
    }

    public @NotNull String getMessage() {
        return message;
    }

    public @NotNull Map<String, String> getDetails() {
        return details;
    }

    /** The message up to its first line break. */
    public @NotNull String getFirstLine() {
        int end = message.length();
        for (int i = 0; i < message.length(); i++) {
            char symbol = message.charAt(i);
            if (symbol == '\n' || symbol == '\r') {
                end = i;
                break;
            }
        }
        return message.substring(0, end);
    }

    /** Short id shared by repeats of the same event: same level, source, first message line and exception. */
    public @NotNull String getFingerprint() {
        String cached = cachedFingerprint;
        if (cached == null) {
            cached = computeFingerprint();
            cachedFingerprint = cached;
        }
        return cached;
    }

    private String computeFingerprint() {
        String firstLine = getFirstLine();
        String exception = details.get("exception");
        String key = level.name() + '\u0000' + source + '\u0000'
            + (firstLine.length() > 200 ? firstLine.substring(0, 200) : firstLine) + '\u0000'
            + (exception == null ? "" : exception);
        String hex = Integer.toHexString(key.hashCode());
        StringBuilder padded = new StringBuilder();
        for (int i = hex.length(); i < 8; i++) {
            padded.append('0');
        }
        return padded.append(hex).toString();
    }

    /** The stack trace of {@code error} with its causes, as text. */
    public static @NotNull String stackTraceOf(@NotNull Throwable error) {
        StringWriter writer = new StringWriter();
        error.printStackTrace(new PrintWriter(writer));
        return writer.toString().trim();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof JournalRecord)) {
            return false;
        }
        JournalRecord record = (JournalRecord) other;
        return timeMillis == record.timeMillis && level == record.level && source.equals(record.source)
            && type.equals(record.type) && message.equals(record.message) && details.equals(record.details);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timeMillis, level, source, type, message, details);
    }

    @Override
    public String toString() {
        return "JournalRecord{timeMillis=" + timeMillis + ", level=" + level + ", source=" + source + ", type=" + type
            + ", message=" + message + ", details=" + details + '}';
    }
}
