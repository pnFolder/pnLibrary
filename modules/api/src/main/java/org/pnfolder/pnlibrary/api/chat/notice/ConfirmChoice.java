package org.pnfolder.pnlibrary.api.chat.notice;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/** One answer on a confirmation card that offers several, such as restarting now or later. */
public final class ConfirmChoice {
    private final String label;
    private final String command;
    private final String hint;

    /**
     * @param label text of the button
     * @param command the command the button runs; a reader that cannot click is told to type it
     * @param hint what the button does, shown on hover
     */
    public ConfirmChoice(@NotNull String label, @NotNull String command, @NotNull String hint) {
        this.label = Objects.requireNonNull(label, "label");
        this.command = Objects.requireNonNull(command, "command");
        this.hint = Objects.requireNonNull(hint, "hint");
    }

    /** The text of the button. */
    public @NotNull String getLabel() {
        return label;
    }

    /** The command the button runs. */
    public @NotNull String getCommand() {
        return command;
    }

    /** What the button does, shown on hover. */
    public @NotNull String getHint() {
        return hint;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ConfirmChoice)) {
            return false;
        }
        ConfirmChoice that = (ConfirmChoice) other;
        return label.equals(that.label) && command.equals(that.command) && hint.equals(that.hint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(label, command, hint);
    }

    @Override
    public String toString() {
        return "ConfirmChoice(label=" + label + ", command=" + command + ", hint=" + hint + ")";
    }
}
