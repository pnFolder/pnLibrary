package org.pnfolder.pnlibrary.core.journal;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** One journal event with everything the journal knows about it, for a menu. */
public final class JournalDetail {
    private final String id;
    private final String level;
    private final String source;
    private final String message;
    private final String stackTrace;
    private final Map<String, String> details;
    private final List<Long> times;

    /**
     * Creates a detail.
     *
     * @param id the id that {@code /pn events view <id>} opens
     * @param level {@code info}, {@code warning} or {@code error}
     * @param source plugin or component that wrote the event
     * @param message the whole text of the newest repeat
     * @param stackTrace the stack trace with its causes, or {@code null} when the event has none
     * @param details other facts the event carries, such as a player or a version
     * @param times when it happened, newest first, in epoch milliseconds
     */
    public JournalDetail(@NotNull String id, @NotNull String level, @NotNull String source, @NotNull String message,
                         @Nullable String stackTrace, @NotNull Map<String, String> details,
                         @NotNull List<Long> times) {
        this.id = id;
        this.level = level;
        this.source = source;
        this.message = message;
        this.stackTrace = stackTrace;
        this.details = details;
        this.times = times;
    }

    public @NotNull String getId() {
        return id;
    }

    public @NotNull String getLevel() {
        return level;
    }

    public @NotNull String getSource() {
        return source;
    }

    public @NotNull String getMessage() {
        return message;
    }

    public @Nullable String getStackTrace() {
        return stackTrace;
    }

    public @NotNull Map<String, String> getDetails() {
        return details;
    }

    public @NotNull List<Long> getTimes() {
        return times;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof JournalDetail)) {
            return false;
        }
        JournalDetail detail = (JournalDetail) other;
        return id.equals(detail.id) && level.equals(detail.level) && source.equals(detail.source)
            && message.equals(detail.message) && Objects.equals(stackTrace, detail.stackTrace)
            && details.equals(detail.details) && times.equals(detail.times);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, level, source, message, stackTrace, details, times);
    }

    @Override
    public String toString() {
        return "JournalDetail{id=" + id + ", level=" + level + ", source=" + source + ", message=" + message
            + ", stackTrace=" + stackTrace + ", details=" + details + ", times=" + times + '}';
    }
}
