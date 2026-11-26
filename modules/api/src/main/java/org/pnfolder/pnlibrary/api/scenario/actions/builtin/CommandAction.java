package org.pnfolder.pnlibrary.api.scenario.actions.builtin;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.audience.OnlinePlayer;
import org.pnfolder.pnlibrary.api.audience.delivery.Delivery;
import org.pnfolder.pnlibrary.api.config.ConfigKey;
import org.pnfolder.pnlibrary.api.scenario.actions.Action;
import org.pnfolder.pnlibrary.api.scenario.actions.ActionContext;
import org.pnfolder.pnlibrary.api.scenario.actions.ActionResult;

import java.util.Objects;

/**
 * Runs a server command. A command the platform refuses fails the scenario, because later steps usually depend on it.
 */
public final class CommandAction implements Action {
    private final String command;
    @ConfigKey("as")
    private final CommandRunner runner;

    /** Creates the action with every setting at its default. */
    public CommandAction() {
        this("", CommandRunner.CONSOLE);
    }

    /** Creates it with the first 1 setting; the rest keep their defaults. */
    public CommandAction(@NotNull String command) {
        this(command, CommandRunner.CONSOLE);
    }

    /** Creates it with every setting given. */
    public CommandAction(@NotNull String command, @NotNull CommandRunner runner) {
        this.command = command;
        this.runner = runner;
    }

    /** Command line without the leading slash; placeholders are filled in. */
    @NotNull
    public String getCommand() {
        return command;
    }

    /** Who runs it. */
    @NotNull
    public CommandRunner getRunner() {
        return runner;
    }

    @NotNull
    @Override
    public ActionResult run(@NotNull ActionContext context) {
        String line = context.format(command).trim();
        if (line.startsWith("/")) {
            line = line.substring(1);
        }
        if (line.isEmpty()) {
            return ActionResult.Done.INSTANCE;
        }
        OnlinePlayer player = context.getPlayer();
        Delivery delivery = runner == CommandRunner.CONSOLE ? player.runConsoleCommand(line) : player.runCommand(line);
        if (delivery.isDelivered()) {
            return ActionResult.Done.INSTANCE;
        }
        return new ActionResult.Failed("command.refused", "The platform did not accept the command '" + line + "'");
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CommandAction)) {
            return false;
        }
        CommandAction that = (CommandAction) other;
        return Objects.equals(command, that.command)
                && Objects.equals(runner, that.runner);
    }

    @Override
    public int hashCode() {
        return Objects.hash(command, runner);
    }

    @NotNull
    @Override
    public String toString() {
        return "CommandAction(command=" + command + ", runner=" + runner + ")";
    }
}
