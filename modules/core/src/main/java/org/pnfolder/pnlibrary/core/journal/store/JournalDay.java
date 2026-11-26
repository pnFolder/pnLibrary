package org.pnfolder.pnlibrary.core.journal.store;

import org.pnfolder.pnlibrary.core.journal.JournalLevel;

import java.time.LocalDate;
import java.util.Objects;

/** The key of an active segment: one level on one day. */
final class JournalDay {
    private final JournalLevel level;
    private final LocalDate date;

    JournalDay(JournalLevel level, LocalDate date) {
        this.level = level;
        this.date = date;
    }

    JournalLevel level() {
        return level;
    }

    LocalDate date() {
        return date;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof JournalDay)) {
            return false;
        }
        JournalDay day = (JournalDay) other;
        return level == day.level && date.equals(day.date);
    }

    @Override
    public int hashCode() {
        return Objects.hash(level, date);
    }
}
