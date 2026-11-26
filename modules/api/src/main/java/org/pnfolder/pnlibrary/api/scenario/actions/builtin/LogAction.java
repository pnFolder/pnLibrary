package org.pnfolder.pnlibrary.api.scenario.actions.builtin;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.logging.LogLevel;
import org.pnfolder.pnlibrary.api.scenario.actions.Action;
import org.pnfolder.pnlibrary.api.scenario.actions.ActionContext;
import org.pnfolder.pnlibrary.api.scenario.actions.ActionResult;

import java.util.Objects;

/** Writes a line to the plugin log. */
public final class LogAction implements Action {
    private final String text;
    private final LogLevel level;

    /** Creates the action with every setting at its default. */
    public LogAction() {
        this("", LogLevel.INFO);
    }

    /** Creates it with the first 1 setting; the rest keep their defaults. */
    public LogAction(@NotNull String text) {
        this(text, LogLevel.INFO);
    }

    /** Creates it with every setting given. */
    public LogAction(@NotNull String text, @NotNull LogLevel level) {
        this.text = Objects.requireNonNull(text, "text");
        this.level = level;
    }

    /** The line; placeholders are filled in. */
    @NotNull
    public String getText() {
        return text;
    }

    /** Severity. */
    @NotNull
    public LogLevel getLevel() {
        return level;
    }

    @NotNull
    @Override
    public ActionResult run(@NotNull ActionContext context) {
        String line = context.format(text);
        switch (level) {
            case DEBUG:
                context.getLogger().debug(line);
                break;
            case SUCCESS:
                context.getLogger().success(line);
                break;
            case WARNING:
                context.getLogger().warning(line);
                break;
            case ERROR:
                context.getLogger().error(line);
                break;
            case CRITICAL:
                context.getLogger().critical(line, null);
                break;
            default:
                context.getLogger().info(line);
                break;
        }
        return ActionResult.Done.INSTANCE;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof LogAction)) {
            return false;
        }
        LogAction that = (LogAction) other;
        return Objects.equals(text, that.text)
                && Objects.equals(level, that.level);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, level);
    }

    @NotNull
    @Override
    public String toString() {
        return "LogAction(text=" + text + ", level=" + level + ")";
    }
}
