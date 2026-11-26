package org.pnfolder.pnlibrary.api.tasks;

/** Resource limits applied by a task service. */
public final class TaskServiceSettings {
    private static final int DEFAULT_HISTORY_CAPACITY = 256;

    private final int historyCapacity;

    /** Settings with the default history of 256 terminal snapshots. */
    public TaskServiceSettings() {
        this(DEFAULT_HISTORY_CAPACITY);
    }

    /**
     * Settings with an explicit history size.
     *
     * @param historyCapacity maximum number of terminal task snapshots retained in memory
     * @throws IllegalArgumentException when negative
     */
    public TaskServiceSettings(int historyCapacity) {
        if (historyCapacity < 0) {
            throw new IllegalArgumentException("Task history capacity must not be negative");
        }
        this.historyCapacity = historyCapacity;
    }

    /** Maximum number of terminal task snapshots retained in memory. */
    public int getHistoryCapacity() {
        return historyCapacity;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof TaskServiceSettings && historyCapacity == ((TaskServiceSettings) other).historyCapacity;
    }

    @Override
    public int hashCode() {
        return historyCapacity;
    }

    @Override
    public String toString() {
        return "TaskServiceSettings(historyCapacity=" + historyCapacity + ")";
    }
}
