package org.pnfolder.pnlibrary.core.tasks;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.plugin.PluginId;
import org.pnfolder.pnlibrary.api.tasks.TaskHandle;
import org.pnfolder.pnlibrary.api.tasks.TaskScope;
import org.pnfolder.pnlibrary.api.tasks.TaskServiceSettings;
import org.pnfolder.pnlibrary.api.tasks.info.TaskId;
import org.pnfolder.pnlibrary.api.tasks.info.TaskQuery;
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskSpec;
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiPredicate;

/** Schedules the tasks of all owners on the platform, keeps the active ones and a history of the finished ones. */
public final class ScheduledTasks implements TaskService {
    private final PlatformTaskAdapter adapter;
    private final TaskErrorLogger errorLogger;
    private final TaskHistory history;
    private final Object lock = new Object();
    private final IdentityHashMap<Object, Map<PluginId, OwnerTaskScope>> scopes =
            new IdentityHashMap<>();
    private final LinkedHashMap<TaskId, ManagedTask> active = new LinkedHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /** A scheduler with the default history size that logs nothing. */
    public ScheduledTasks(@NotNull PlatformTaskAdapter adapter) {
        this(adapter, new TaskServiceSettings(), (owner, message, error) -> { }, null);
    }

    /** A scheduler that keeps {@code settings} many finished tasks, in {@code store} when given. */
    public ScheduledTasks(
            @NotNull PlatformTaskAdapter adapter,
            @NotNull TaskServiceSettings settings,
            @NotNull TaskErrorLogger errorLogger,
            @Nullable TaskHistoryStore store) {
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.errorLogger = Objects.requireNonNull(errorLogger, "errorLogger");
        this.history = new TaskHistory(settings.getHistoryCapacity(), store);
    }

    @NotNull
    @Override
    public TaskScope scope(@NotNull Object owner) {
        String simple = owner.getClass().getSimpleName();
        return scope(owner, PluginId.of(simple.trim().isEmpty() ? "owner" : simple));
    }

    @NotNull
    @Override
    public TaskScope scope(@NotNull Object owner, @NotNull PluginId scopeId) {
        synchronized (scopes) {
            if (closed.get()) {
                throw new IllegalStateException("TaskService is closed");
            }
            Map<PluginId, OwnerTaskScope> owned = scopes.get(owner);
            if (owned == null) {
                owned = new LinkedHashMap<>();
                scopes.put(owner, owned);
            }
            OwnerTaskScope scope = owned.get(scopeId);
            if (scope == null) {
                scope = new OwnerTaskScope(this, owner, scopeId);
                owned.put(scopeId, scope);
            }
            return scope;
        }
    }

    @Nullable
    @Override
    public TaskHandle get(@NotNull TaskId id) {
        synchronized (lock) {
            return active.get(id);
        }
    }

    @NotNull
    @Override
    public List<TaskSnapshot> query(@NotNull TaskQuery query) {
        return snapshots(query, (owner, scopeToken) -> true);
    }

    @NotNull
    @Override
    public List<TaskSnapshot> query(@NotNull Object owner, @NotNull TaskQuery query) {
        return snapshots(query, (taskOwner, scopeToken) -> taskOwner == owner);
    }

    @NotNull
    @Override
    public TaskControl cancelByAdmin(@NotNull TaskId id) {
        ManagedTask task = activeTask(id);
        if (task == null) {
            return TaskControl.UNAVAILABLE;
        }
        if (!task.getSpec().getCancellable()) {
            return TaskControl.FORBIDDEN;
        }
        return task.cancelIfActive() ? TaskControl.DONE : TaskControl.UNAVAILABLE;
    }

    @NotNull
    @Override
    public TaskControl runNow(@NotNull TaskId id) {
        ManagedTask task = activeTask(id);
        if (task == null) {
            return TaskControl.UNAVAILABLE;
        }
        if (!task.getSpec().getRunnableNow()) {
            return TaskControl.FORBIDDEN;
        }
        return task.runNow() ? TaskControl.DONE : TaskControl.UNAVAILABLE;
    }

    @Override
    public void close(@NotNull Object owner) {
        List<OwnerTaskScope> current;
        synchronized (scopes) {
            Map<PluginId, OwnerTaskScope> removed = scopes.remove(owner);
            current = removed == null ? Collections.emptyList() : new ArrayList<>(removed.values());
        }
        TaskCleanup.closeAll(cancellations(current));
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        List<OwnerTaskScope> current = new ArrayList<>();
        synchronized (scopes) {
            for (Map<PluginId, OwnerTaskScope> owned : scopes.values()) {
                current.addAll(owned.values());
            }
            scopes.clear();
        }
        List<Runnable> steps = cancellations(current);
        steps.add(adapter::close);
        steps.add(history::save);
        TaskCleanup.closeAll(steps);
    }

    private static List<Runnable> cancellations(List<OwnerTaskScope> current) {
        List<Runnable> steps = new ArrayList<>();
        for (final OwnerTaskScope scope : current) {
            steps.add(scope::cancelAll);
        }
        return steps;
    }

    private ManagedTask activeTask(TaskId id) {
        synchronized (lock) {
            return active.get(id);
        }
    }

    /** Active and finished tasks matching {@code query} whose owner and scope token pass {@code belongs}. */
    List<TaskSnapshot> snapshots(TaskQuery query, BiPredicate<Object, Object> belongs) {
        List<TaskSnapshot> all = new ArrayList<>();
        synchronized (lock) {
            for (ManagedTask task : active.values()) {
                if (belongs.test(task.getOwner(), task.getScopeToken())) {
                    all.add(task.snapshot());
                }
            }
            all.addAll(history.matching(belongs));
        }
        List<TaskSnapshot> matching = new ArrayList<>();
        for (TaskSnapshot snapshot : all) {
            if (query.matches(snapshot)) {
                matching.add(snapshot);
            }
        }
        return Collections.unmodifiableList(matching);
    }

    Object getLock() {
        return lock;
    }

    Map<TaskId, ManagedTask> getActive() {
        return active;
    }

    boolean isClosed() {
        return closed.get();
    }

    PlatformTaskAdapter getAdapter() {
        return adapter;
    }

    /** Removes {@code scope} of {@code owner} from the registry once it is closed. */
    void forgetScope(Object owner, PluginId scopeId, OwnerTaskScope scope) {
        synchronized (scopes) {
            Map<PluginId, OwnerTaskScope> owned = scopes.get(owner);
            if (owned == null) {
                return;
            }
            if (owned.get(scopeId) == scope) {
                owned.remove(scopeId);
            }
            if (owned.isEmpty()) {
                scopes.remove(owner);
            }
        }
    }

    ManagedTask newTask(Object owner, Object scopeToken, TaskSpec spec, OwnerTaskScope scope, @Nullable TaskId parent) {
        return new ManagedTask(owner, scopeToken, spec, adapter, errorLogger, this::archive, scope::scheduleUnder, parent);
    }

    /** Moves a finished task from the active list to the history; does nothing if it was already moved. */
    private void archive(ManagedTask task) {
        boolean archived;
        synchronized (lock) {
            if (active.remove(task.getId()) == null) {
                return;
            }
            archived = history.add(task.getOwner(), task.getScopeToken(), task.snapshot());
        }
        if (archived) {
            history.saveIfDue();
        }
    }
}
