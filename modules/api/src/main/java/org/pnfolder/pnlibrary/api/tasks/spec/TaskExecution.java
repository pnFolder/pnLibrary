package org.pnfolder.pnlibrary.api.tasks.spec;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Platform execution context selected for a task. */
public final class TaskExecution {
    private final Kind kind;
    private final Object target;

    private TaskExecution(Kind kind, Object target) {
        this.kind = kind;
        this.target = target;
    }

    /** Selects the platform's global execution context. */
    @NotNull
    public static TaskExecution global() {
        return new TaskExecution(Kind.GLOBAL, null);
    }

    /** Selects pnLibrary's asynchronous executor. */
    @NotNull
    public static TaskExecution async() {
        return new TaskExecution(Kind.ASYNC, null);
    }

    /** Selects the execution context associated with native {@code target}. */
    @NotNull
    public static TaskExecution entity(@NotNull Object target) {
        return new TaskExecution(Kind.ENTITY, target);
    }

    /** Execution-context category. */
    @NotNull
    public Kind getKind() {
        return kind;
    }

    /** Platform-native entity for {@link Kind#ENTITY}, otherwise {@code null}. */
    @Nullable
    public Object getTarget() {
        return target;
    }

    @Override
    public String toString() {
        return target == null ? kind.name() : kind + "(" + target + ")";
    }

    /** Supported execution-context categories. */
    public enum Kind {
        /** The platform's global (main) context. */
        GLOBAL,
        /** pnLibrary's asynchronous executor. */
        ASYNC,
        /** The context bound to one native entity, such as a Folia region. */
        ENTITY
    }
}
