package org.pnfolder.pnlibrary.core.commands.engine;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.commands.error.CommandError;
import org.pnfolder.pnlibrary.api.commands.error.CommandErrorHandler;

/** A module's scope chains to its plugin's scope, so the error waterfall runs: node, module, plugin, library. */
public final class CommandFeedbackScope {
    private final CommandFeedbackScope parent;
    private final ConcurrentHashMap<Class<? extends CommandError>, CommandErrorHandler<?>> handlers =
            new ConcurrentHashMap<>();

    public CommandFeedbackScope(@Nullable CommandFeedbackScope parent) {
        this.parent = parent;
    }

    public CommandFeedbackScope() {
        this(null);
    }

    @Nullable
    public CommandFeedbackScope getParent() {
        return parent;
    }

    @NotNull
    public ConcurrentHashMap<Class<? extends CommandError>, CommandErrorHandler<?>> getHandlers() {
        return handlers;
    }

    /** The handler for the exact class of {@code error}, or else for its nearest base class. */
    @Nullable
    @SuppressWarnings("unchecked")
    public static CommandErrorHandler<CommandError> handlerFor(
            @NotNull Map<Class<? extends CommandError>, CommandErrorHandler<?>> handlers, @NotNull CommandError error) {
        Class<?> type = error.getClass();
        while (type != null && CommandError.class.isAssignableFrom(type)) {
            CommandErrorHandler<?> handler = handlers.get(type);
            if (handler != null) {
                return (CommandErrorHandler<CommandError>) handler;
            }
            type = type.getSuperclass();
        }
        return null;
    }
}
