package org.pnfolder.pnlibrary.api.commands.error;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.commands.CommandContext;

/**
 * Answers an error. {@code context} tells who ran which command; the sender can be messaged, played a sound, anything.
 */
@FunctionalInterface
public interface CommandErrorHandler<E extends CommandError> {
    /** Called with the error; throwing here is logged and the next level answers instead. */
    void handle(@NotNull CommandContext context, @NotNull E error);
}
