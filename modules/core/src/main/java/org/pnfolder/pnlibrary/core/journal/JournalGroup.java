package org.pnfolder.pnlibrary.core.journal;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** Equal journal events merged into one line, for menus. */
public final class JournalGroup {
    private final String id;
    private final String level;
    private final String source;
    private final String message;
    private final int count;
    private final long lastMillis;

    /**
     * Creates a group.
     *
     * @param id the id that {@code /pn events view <id>} opens
     * @param level {@code info}, {@code warning} or {@code error}
     * @param source plugin or component that wrote the event
     * @param message the first line of the event text
     * @param count how many times it happened in the window
     * @param lastMillis when it happened last, in epoch milliseconds
     */
    public JournalGroup(@NotNull String id, @NotNull String level, @NotNull String source, @NotNull String message,
                        int count, long lastMillis) {
        this.id = Objects.requireNonNull(id, "id");
        this.level = Objects.requireNonNull(level, "level");
        this.source = Objects.requireNonNull(source, "source");
        this.message = Objects.requireNonNull(message, "message");
        this.count = count;
        this.lastMillis = lastMillis;
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

    public int getCount() {
        return count;
    }

    public long getLastMillis() {
        return lastMillis;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof JournalGroup)) {
            return false;
        }
        JournalGroup group = (JournalGroup) other;
        return count == group.count && lastMillis == group.lastMillis && id.equals(group.id)
            && level.equals(group.level) && source.equals(group.source) && message.equals(group.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, level, source, message, count, lastMillis);
    }

    @Override
    public String toString() {
        return "JournalGroup{id=" + id + ", level=" + level + ", source=" + source + ", message=" + message
            + ", count=" + count + ", lastMillis=" + lastMillis + '}';
    }
}
