package org.pnfolder.pnlibrary.core.commands.engine;

import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.commands.definition.CommandNode;
import org.pnfolder.pnlibrary.api.commands.error.CommandError;

/** A typed command that cannot run; {@code nodes} runs from the deepest node reached to the root. */
public final class RouteProblem extends CommandRoute {
    private final CommandError error;
    private final List<CommandNode> nodes;

    public RouteProblem(@NotNull CommandError error, @NotNull List<CommandNode> nodes) {
        this.error = error;
        this.nodes = nodes;
    }

    @NotNull
    public CommandError getError() {
        return error;
    }

    @NotNull
    public List<CommandNode> getNodes() {
        return nodes;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RouteProblem)) {
            return false;
        }
        RouteProblem that = (RouteProblem) other;
        return error.equals(that.error) && nodes.equals(that.nodes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(error, nodes);
    }

    @Override
    public String toString() {
        return "RouteProblem(error=" + error.getClass().getSimpleName() + ")";
    }
}
