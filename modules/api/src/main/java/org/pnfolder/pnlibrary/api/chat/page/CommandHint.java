package org.pnfolder.pnlibrary.api.chat.page;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** One line of a {@code «Команды»} section: a command and what it does. */
public final class CommandHint {
    private final String command;
    private final String purpose;

    /**
     * @param command the command with placeholders such as {@code <игрок>}, or {@code null} to leave the line out
     * @param purpose what the command does
     */
    public CommandHint(@Nullable String command, @NotNull String purpose) {
        this.command = command;
        this.purpose = Objects.requireNonNull(purpose, "purpose");
    }

    /** The command, or {@code null} when the line is left out. */
    public @Nullable String getCommand() {
        return command;
    }

    /** What the command does. */
    public @NotNull String getPurpose() {
        return purpose;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CommandHint)) {
            return false;
        }
        CommandHint that = (CommandHint) other;
        return Objects.equals(command, that.command) && purpose.equals(that.purpose);
    }

    @Override
    public int hashCode() {
        return Objects.hash(command, purpose);
    }

    @Override
    public String toString() {
        return "CommandHint(command=" + command + ", purpose=" + purpose + ")";
    }
}
