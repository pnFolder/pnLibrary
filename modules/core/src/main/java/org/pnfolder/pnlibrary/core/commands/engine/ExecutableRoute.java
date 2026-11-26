package org.pnfolder.pnlibrary.core.commands.engine;

import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.definition.CommandNode;

/** A typed command that reached a node it can run, with the context that holds the parsed values. */
public final class ExecutableRoute extends CommandRoute {
    private final CommandNode node;
    private final CommandContext context;
    private final List<CommandNode> path;

    public ExecutableRoute(@NotNull CommandNode node, @NotNull CommandContext context, @NotNull List<CommandNode> path) {
        this.node = Objects.requireNonNull(node, "node");
        this.context = Objects.requireNonNull(context, "context");
        this.path = Objects.requireNonNull(path, "path");
    }

    @NotNull
    public CommandNode getNode() {
        return node;
    }

    @NotNull
    public CommandContext getContext() {
        return context;
    }

    @NotNull
    public List<CommandNode> getPath() {
        return path;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ExecutableRoute)) {
            return false;
        }
        ExecutableRoute that = (ExecutableRoute) other;
        return node.equals(that.node) && context.equals(that.context) && path.equals(that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(node, context, path);
    }

    @Override
    public String toString() {
        return "ExecutableRoute(node=" + node.getName() + ", path=" + path.size() + ")";
    }
}
