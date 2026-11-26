package org.pnfolder.pnlibrary.api.commands.error;

import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;

/** Ways to stop a command with an error from inside a handler. */
public final class CommandErrors {
    private CommandErrors() {}

    /**
     * Stops the command with {@code error}; never returns. The return type lets a caller write
     * {@code throw CommandErrors.fail(error)} so the compiler sees the end of the path.
     */
    @NotNull
    public static CommandException fail(@NotNull CommandError error) {
        throw new CommandException(error);
    }

    /**
     * Stops the command with the error made by {@code error} unless {@code condition} holds, the way
     * {@code require} and {@code check} do: {@code ensure(balance >= price, () -> new NoMoney(price))}. The supplier
     * is called only when it is needed.
     */
    public static void ensure(boolean condition, @NotNull Supplier<? extends CommandError> error) {
        if (!condition) {
            throw new CommandException(error.get());
        }
    }
}
