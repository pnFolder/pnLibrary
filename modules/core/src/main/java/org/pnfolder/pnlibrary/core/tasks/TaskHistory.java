package org.pnfolder.pnlibrary.core.tasks;

import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.tasks.info.TaskId;
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.BiPredicate;

/** The latest finished tasks, up to a capacity, written to the store at most every half minute. */
final class TaskHistory {
    private static final long SAVE_EVERY_MILLIS = 30_000L;

    private final LinkedHashMap<TaskId, ArchivedTask> entries = new LinkedHashMap<>();
    private final int capacity;
    private final TaskHistoryStore store;
    private long lastSaved;

    TaskHistory(int capacity, @Nullable TaskHistoryStore store) {
        this.capacity = capacity;
        this.store = store;
        if (store != null) {
            loadEarlierRuns(store);
        }
    }

    // Tasks of earlier runs have no owner object any more, so only the global list shows them.
    private void loadEarlierRuns(TaskHistoryStore source) {
        List<TaskSnapshot> loaded = source.load();
        int keep = Math.max(capacity, 0);
        for (TaskSnapshot snapshot : loaded.subList(Math.max(0, loaded.size() - keep), loaded.size())) {
            entries.put(snapshot.getId(), new ArchivedTask(new Object(), new Object(), snapshot));
        }
    }

    /** Adds a finished task, dropping the oldest above the capacity; false when the history keeps nothing. */
    synchronized boolean add(Object owner, Object scopeToken, TaskSnapshot snapshot) {
        if (capacity <= 0) {
            return false;
        }
        entries.put(snapshot.getId(), new ArchivedTask(owner, scopeToken, snapshot));
        while (entries.size() > capacity) {
            Iterator<TaskId> oldest = entries.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
        return true;
    }

    /** The snapshots of the tasks whose owner and scope token pass {@code belongs}. */
    synchronized List<TaskSnapshot> matching(BiPredicate<Object, Object> belongs) {
        List<TaskSnapshot> found = new ArrayList<>();
        for (ArchivedTask archived : entries.values()) {
            if (belongs.test(archived.getOwner(), archived.getScopeToken())) {
                found.add(archived.getSnapshot());
            }
        }
        return found;
    }

    /** Writes the history at most every half minute, so a plugin that runs many short tasks does not hammer the disk. */
    void saveIfDue() {
        long now = System.currentTimeMillis();
        boolean due;
        synchronized (this) {
            due = now - lastSaved >= SAVE_EVERY_MILLIS;
            if (due) {
                lastSaved = now;
            }
        }
        if (due) {
            save();
        }
    }

    void save() {
        if (store == null) {
            return;
        }
        store.save(matching((owner, scopeToken) -> true));
    }
}
