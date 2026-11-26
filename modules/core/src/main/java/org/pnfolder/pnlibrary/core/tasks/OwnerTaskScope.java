package org.pnfolder.pnlibrary.core.tasks;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.plugin.PluginId;
import org.pnfolder.pnlibrary.api.tasks.TaskHandle;
import org.pnfolder.pnlibrary.api.tasks.TaskScope;
import org.pnfolder.pnlibrary.api.tasks.info.TaskId;
import org.pnfolder.pnlibrary.api.tasks.info.TaskQuery;
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskSpec;
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskBuilder;
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskHandle;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** The tasks one owner scheduled under one name; cancelling the scope cancels all of them. */
final class OwnerTaskScope implements TaskScope {
    private final ScheduledTasks service;
    private final Object owner;
    private final PluginId scopeId;
    private final Object scopeToken = new Object();
    private final AtomicBoolean scopeClosed = new AtomicBoolean(false);

    OwnerTaskScope(ScheduledTasks service, Object owner, PluginId scopeId) {
        this.service = service;
        this.owner = owner;
        this.scopeId = scopeId;
    }

    @NotNull
    @Override
    public Object getOwner() {
        return owner;
    }

    @NotNull
    @Override
    public TaskHandle schedule(@NotNull TaskSpec spec) {
        return scheduleUnder(spec, null);
    }

    /** Schedules {@code spec} as a task of this scope, started by task {@code parent} when there is one. */
    TaskHandle scheduleUnder(TaskSpec spec, @Nullable TaskId parent) {
        if (scopeClosed.get() || service.isClosed()) {
            throw new IllegalStateException("TaskScope is closed");
        }
        synchronized (service.getLock()) {
            ManagedTask existing = resolveConflict(spec);
            if (existing != null) {
                return existing;
            }
            ManagedTask managed = service.newTask(owner, scopeToken, spec, this, parent);
            service.getActive().put(managed.getId(), managed);
            PlatformTaskHandle platform = register(managed, spec);
            if (scopeClosed.get() || service.isClosed()) {
                service.getActive().remove(managed.getId());
                platform.cancel();
                throw new IllegalStateException("TaskScope is closed");
            }
            managed.attach(platform);
            return managed;
        }
    }

    /** The task to return instead of scheduling {@code spec}, or {@code null} to go on; may cancel the one it replaces. */
    @Nullable
    private ManagedTask resolveConflict(TaskSpec spec) {
        ManagedTask existing = spec.getKey() == null ? null : byKey(spec.getKey());
        if (existing == null) {
            return null;
        }
        switch (spec.getConflictPolicy()) {
            case REJECT:
                throw new IllegalStateException("Task key '" + spec.getKey() + "' is already active for this owner");
            case KEEP_EXISTING:
                return existing;
            default:
                existing.cancel();
                return null;
        }
    }

    private PlatformTaskHandle register(ManagedTask managed, TaskSpec spec) {
        try {
            PlatformTaskBuilder builder = service.getAdapter().buildTask()
                    .task(managed::invoke)
                    .execution(spec.getExecution().getKind(), spec.getExecution().getTarget())
                    .delay(spec.getDelay());
            if (spec.getInterval() != null) {
                builder.every(spec.getInterval());
            }
            return builder.schedule();
        } catch (Throwable error) {
            service.getActive().remove(managed.getId());
            throw error;
        }
    }

    private ManagedTask byKey(String key) {
        for (ManagedTask task : service.getActive().values()) {
            if (task.getScopeToken() == scopeToken && key.equals(task.getSpec().getKey())) {
                return task;
            }
        }
        return null;
    }

    @Nullable
    @Override
    public TaskHandle get(@NotNull TaskId id) {
        synchronized (service.getLock()) {
            ManagedTask task = service.getActive().get(id);
            return task != null && task.getScopeToken() == scopeToken ? task : null;
        }
    }

    @Nullable
    @Override
    public TaskHandle getByKey(@NotNull String key) {
        synchronized (service.getLock()) {
            return byKey(key);
        }
    }

    @NotNull
    @Override
    public List<TaskSnapshot> query(@NotNull TaskQuery query) {
        return service.snapshots(query, (taskOwner, token) -> token == scopeToken);
    }

    @Override
    public void cancelAll() {
        if (!scopeClosed.compareAndSet(false, true)) {
            return;
        }
        List<Runnable> steps = new ArrayList<>();
        synchronized (service.getLock()) {
            for (ManagedTask task : service.getActive().values()) {
                if (task.getScopeToken() == scopeToken) {
                    steps.add(task::cancel);
                }
            }
        }
        try {
            TaskCleanup.closeAll(steps);
        } finally {
            service.forgetScope(owner, scopeId, this);
        }
    }
}
