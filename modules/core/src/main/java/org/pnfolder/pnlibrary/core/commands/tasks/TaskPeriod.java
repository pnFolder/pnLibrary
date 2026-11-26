package org.pnfolder.pnlibrary.core.commands.tasks;

import java.time.Duration;
import java.time.Instant;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot;

/** Age bucket of a finished task in the task list; {@link #getWord()} is the command argument. */
public enum TaskPeriod {
    TODAY("today", "сегодня"),
    WEEK("week", "7 дней"),
    OLDER("older", "раньше"),
    ANY("any", "любое время");

    private static final long WEEK_DAYS = 7L;

    private final String word;
    private final String label;

    TaskPeriod(String word, String label) {
        this.word = word;
        this.label = label;
    }

    @NotNull
    public String getWord() {
        return word;
    }

    @NotNull
    public String getLabel() {
        return label;
    }

    /** Whether a task of this age belongs to the period; a task that is still active belongs to the recent ones. */
    public boolean keep(@NotNull TaskSnapshot task) {
        switch (this) {
            case TODAY:
                return TaskLabels.isToday(task) || task.isActive();
            case WEEK:
                return TaskLabels.activity(task).isAfter(Instant.now().minus(Duration.ofDays(WEEK_DAYS))) || task.isActive();
            case OLDER:
                return !TaskLabels.isToday(task) && !task.isActive();
            default:
                return true;
        }
    }
}
