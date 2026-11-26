package org.pnfolder.pnlibrary.api.commands.definition;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.commands.CommandContext;

/** Synchronous access rule evaluated with values parsed before this node. */
@FunctionalInterface
public interface CommandAvailability {
    /** Returns whether this node is visible and executable for {@code context}. */
    boolean isAvailable(@NotNull CommandContext context);
}
