package org.pnfolder.pnlibrary.core.commands.engine;

import java.util.Objects;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.CommandRegistration;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.api.commands.error.CommandException;
import org.pnfolder.pnlibrary.api.commands.error.CommandFailed;
import org.pnfolder.pnlibrary.api.logging.LogLevel;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandAdapter;
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandDispatcher;
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandRegistration;
import org.pnfolder.pnlibrary.spi.platform.PlatformLogging;

/** Registers portable commands with the platform and runs them: routing, error answers, suggestions, counters. */
public final class CommandRegistry implements CommandService, PlatformCommandDispatcher, AutoCloseable {
    private final PlatformCommandAdapter commandAdapter;
    private final PlatformLogging logging;
    private final StandardCommandFeedback standard;
    private final CommandIndex index = new CommandIndex();
    private final CommandStats stats = new CommandStats();
    private final CommandAnswers answers;
    private final CommandTreeRouter router = new CommandTreeRouter();

    public CommandRegistry(@NotNull PlatformCommandAdapter commandAdapter, @NotNull PlatformLogging logging,
            @NotNull StandardCommandFeedback standard) {
        this.commandAdapter = Objects.requireNonNull(commandAdapter, "commandAdapter");
        this.logging = Objects.requireNonNull(logging, "logging");
        this.standard = Objects.requireNonNull(standard, "standard");
        this.answers = new CommandAnswers(standard, logging);
    }

    public CommandRegistry(@NotNull PlatformCommandAdapter commandAdapter, @NotNull PlatformLogging logging) {
        this(commandAdapter, logging, new StandardCommandFeedback());
    }

    @NotNull
    @Override
    public CommandRegistration register(@NotNull Object owner, @NotNull CommandDefinition command,
            @Nullable CommandFeedbackScope feedback) {
        Set<String> names = new LinkedHashSet<>();
        names.add(command.getName());
        names.addAll(command.getAliases());
        index.reserve(names);
        final PlatformCommandRegistration nativeRegistration;
        try {
            nativeRegistration = commandAdapter.register(owner, command, this);
        } catch (Throwable error) {
            index.release(names);
            throw error;
        }
        RegisteredCommand registration = new RegisteredCommand(owner, command, names, nativeRegistration, feedback, index);
        index.add(registration, names, () -> nativeRegistration.close());
        return registration;
    }

    @Override
    public void unregisterOwner(@NotNull Object owner) {
        List<RegisteredCommand> owned = index.ownedBy(owner);
        Collections.reverse(owned);
        closeRegistrations(owned);
    }

    @NotNull
    @Override
    public CompletionStage<Void> execute(@NotNull CommandDefinition command, @NotNull CommandContext context) {
        stats.executed(command.getName());
        RegisteredCommand registration = index.active(command);
        if (registration == null) {
            return CommandDefinition.completedExecution();
        }
        CommandRoute route;
        try {
            route = router.route(command, context);
        } catch (Throwable error) {
            Failures.rethrowIfFatal(error);
            executionFailed(registration.getOwner(), command.getName(), context, error);
            return CommandDefinition.completedExecution();
        }
        if (route instanceof RouteProblem) {
            RouteProblem problem = (RouteProblem) route;
            answers.answer(registration, context, problem.getError(), problem.getNodes());
            return CommandDefinition.completedExecution();
        }
        return run(registration, (ExecutableRoute) route);
    }

    private CompletionStage<Void> run(final RegisteredCommand registration, final ExecutableRoute executable) {
        CompletionStage<Void> stage;
        try {
            stage = executable.getNode().getExecution().execute(executable.getContext());
        } catch (Throwable error) {
            Failures.rethrowIfFatal(error);
            failed(registration, executable, error);
            return CommandDefinition.completedExecution();
        }
        return stage.handle((ignored, error) -> {
            if (error != null) {
                failed(registration, executable, unwrap(error));
            }
            return null;
        });
    }

    // CommandException = intentional error, answered through the normal chain. Anything else is logged and shown as a failure.
    private void failed(RegisteredCommand registration, ExecutableRoute executable, Throwable error) {
        CommandException failure = null;
        for (Throwable at = error; at != null && failure == null; at = at.getCause()) {
            if (at instanceof CommandException) {
                failure = (CommandException) at;
            }
        }
        if (failure == null) {
            executionFailed(registration.getOwner(), registration.getCommand().getName(), executable.getContext(), error);
            return;
        }
        List<org.pnfolder.pnlibrary.api.commands.definition.CommandNode> deepestFirst =
                new ArrayList<>(executable.getPath());
        Collections.reverse(deepestFirst);
        answers.answer(registration, executable.getContext(), failure.getError(), deepestFirst);
    }

    @NotNull
    @Override
    public CompletionStage<List<String>> suggest(@NotNull CommandDefinition command, @NotNull CommandContext context) {
        stats.suggested();
        final RegisteredCommand registration = index.active(command);
        if (registration == null) {
            return CommandDefinition.completedSuggestions(Collections.emptyList());
        }
        CompletionStage<List<String>> stage;
        try {
            stage = router.suggest(command, context);
        } catch (Throwable error) {
            Failures.rethrowIfFatal(error);
            suggestionFailed(registration.getOwner(), error);
            return CommandDefinition.completedSuggestions(Collections.emptyList());
        }
        return stage.handle((suggestions, error) -> {
            if (error != null) {
                suggestionFailed(registration.getOwner(), unwrap(error));
                return Collections.emptyList();
            }
            return suggestions == null ? Collections.emptyList() : new ArrayList<>(suggestions);
        });
    }

    @Override
    public void close() {
        if (!index.close()) {
            return;
        }
        List<RegisteredCommand> current = index.all();
        Collections.reverse(current);
        closeRegistrations(current);
        try {
            commandAdapter.close();
        } catch (Throwable error) {
            Failures.rethrowIfFatal(error);
            logging.log(this, LogLevel.ERROR, "Command adapter cleanup failed", error);
        }
    }

    private void executionFailed(Object owner, String commandName, CommandContext context, Throwable error) {
        stats.executionFailed(commandName);
        logging.log(owner, LogLevel.ERROR, "Command execution failed", error);
        standard.answer(context, new CommandFailed());
    }

    private void suggestionFailed(Object owner, Throwable error) {
        stats.suggestionFailed();
        logging.log(owner, LogLevel.ERROR, "Command suggestions failed", error);
    }

    /** Counters and the names of the open commands, for the diagnostic report. */
    @NotNull
    public Map<String, Object> diagnosticSnapshot() {
        return stats.snapshot(index.open());
    }

    private void closeRegistrations(List<RegisteredCommand> current) {
        for (RegisteredCommand registration : current) {
            try {
                registration.close();
            } catch (Throwable error) {
                Failures.rethrowIfFatal(error);
                logging.log(registration.getOwner(), LogLevel.ERROR, "Command cleanup failed", error);
            }
        }
    }

    // Strips the CompletionException wrapper added by the stage; keeps the original cause.
    private static Throwable unwrap(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }
}
