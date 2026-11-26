package org.pnfolder.pnlibrary.core.commands.tasks;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot;
import org.pnfolder.pnlibrary.api.tasks.info.TaskStatus;

/** Task filter of the task list; {@link #getWord()} is the command argument. */
public enum TaskState {
    ALL("all", "все"),
    ACTIVE("active", "работают"),
    FAILED("failed", "с ошибками"),
    DONE("done", "закончены");

    private final String word;
    private final String label;

    TaskState(String word, String label) {
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

    public boolean keep(@NotNull TaskSnapshot task) {
        switch (this) {
            case ACTIVE:
                return task.isActive();
            case FAILED:
                return task.getStatus() == TaskStatus.FAILED || task.getLastFailure() != null;
            case DONE:
                return task.getStatus() == TaskStatus.COMPLETED || task.getStatus() == TaskStatus.CANCELLED;
            default:
                return true;
        }
    }
}
