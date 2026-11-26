package org.pnfolder.pnlibrary.api.commands.definition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.error.CommandError;
import org.pnfolder.pnlibrary.api.commands.error.CommandErrorHandler;

/** Mutable construction DSL that produces an immutable {@link CommandDefinition}. */
public final class CommandBuilder {
    private final String name;
    private final Set<String> aliases = new LinkedHashSet<>();
    private String permission;
    private List<String> alsoPermissions = Collections.emptyList();
    private boolean consoleBypassesPermission;
    private String noPermissionMessage;
    private final Map<Class<? extends CommandError>, CommandErrorHandler<?>> errorHandlers =
            new LinkedHashMap<>();
    private SenderRule senderRule = SenderRule.ANY;
    private CommandHandler execution = context -> CommandDefinition.completedExecution();
    private boolean executable;
    private boolean acceptsExtraArguments;
    private SuggestionHandler suggestions = context -> CommandDefinition.completedSuggestions(Collections.emptyList());
    private CommandAvailability availability = context -> true;
    private final List<CommandNodeBuilder> children = new ArrayList<>();
    private final List<CommandNode> mounted = new ArrayList<>();

    CommandBuilder(String name) {
        this.name = CommandDefinition.normalizeCommandName(name, "command name");
    }

    static String nonBlank(String value, String message) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }

    /** Adds alternative root labels that run the same command. */
    @NotNull
    public CommandBuilder aliases(@NotNull String... values) {
        for (String value : values) {
            String alias = CommandDefinition.normalizeCommandName(value, "command alias", CommandDefinition.ALIAS_NAME);
            if (alias.equals(name)) {
                throw new IllegalArgumentException("Command alias must differ from the primary name");
            }
            aliases.add(alias);
        }
        return this;
    }

    /** Requires the non-blank root permission {@code value}. */
    @NotNull
    public CommandBuilder permission(@NotNull String value) {
        return permission(value, new String[0]);
    }

    /** Requires the non-blank root permission {@code value}; with {@code others} a sender needs only one of them. */
    @NotNull
    public CommandBuilder permission(@NotNull String value, @NotNull String... others) {
        permission = nonBlank(value, "Command permission must not be blank");
        List<String> also = new ArrayList<>();
        for (String other : others) {
            also.add(nonBlank(other, "Command permission must not be blank"));
        }
        alsoPermissions = also;
        return this;
    }

    /**
     * Sets the message shown to a sender who lacks the root permission, for example
     * {@code noPermissionMessage("У вас нет права на /pn events. Попросите администратора.")}.
     */
    @NotNull
    public CommandBuilder noPermissionMessage(@NotNull String value) {
        noPermissionMessage = nonBlank(value, "No-permission message must not be blank");
        return this;
    }

    /**
     * Answers errors of class {@code type}, and of every class derived from it, with {@code handler}: message the
     * sender, play a sound, notify staff, anything. The most specific level wins: a subcommand over the command, the
     * command over the module, the module over the plugin, the plugin over the library's standard answer. On one level
     * the handler of the nearest class wins, so {@code NoPermission} is answered before {@code CommandError}.
     */
    @NotNull
    public <E extends CommandError> CommandBuilder onError(@NotNull Class<E> type, @NotNull CommandErrorHandler<E> handler) {
        errorHandlers.put(type, handler);
        return this;
    }

    /** Answers errors of class {@code type} with the plain {@code message}; {@code {name}} fills in from the error. */
    @NotNull
    public <E extends CommandError> CommandBuilder onError(@NotNull Class<E> type, @NotNull String message) {
        final String text = nonBlank(message, "Error message must not be blank");
        errorHandlers.put(type, (CommandErrorHandler<E>) (context, error) ->
                context.getSender().sendMessage(Component.text(error.format(text))));
        return this;
    }

    /** Lets only players use this command; the console gets the {@code PlayerOnly} answer. */
    @NotNull
    public CommandBuilder playerOnly() {
        senderRule = SenderRule.PLAYER;
        return this;
    }

    /** Lets only the console use this command; players get the {@code ConsoleOnly} answer. */
    @NotNull
    public CommandBuilder consoleOnly() {
        senderRule = SenderRule.CONSOLE;
        return this;
    }

    /** Lets the console ignore the root permission. */
    @NotNull
    public CommandBuilder consoleBypassesPermission() {
        return consoleBypassesPermission(true);
    }

    /** Configures whether the console bypasses the root permission. */
    @NotNull
    public CommandBuilder consoleBypassesPermission(boolean enabled) {
        consoleBypassesPermission = enabled;
        return this;
    }

    /**
     * Lets the root run when the sender types words that none of its children match, instead of answering with an
     * unknown-argument error; the handler reads them from {@code CommandContext.getArguments()}.
     */
    @NotNull
    public CommandBuilder acceptsExtraArguments() {
        acceptsExtraArguments = true;
        return this;
    }

    /** Installs a synchronous root execution {@code handler}. */
    @NotNull
    public CommandBuilder executes(@NotNull final Consumer<CommandContext> handler) {
        return executesAsync(context -> {
            handler.accept(context);
            return CommandDefinition.completedExecution();
        });
    }

    /** Installs an asynchronous root execution {@code handler}. */
    @NotNull
    public CommandBuilder executesAsync(@NotNull CommandHandler handler) {
        executable = true;
        execution = handler;
        return this;
    }

    /** Installs a synchronous root suggestion {@code handler}. */
    @NotNull
    public CommandBuilder suggests(@NotNull final Function<CommandContext, List<String>> handler) {
        return suggestsAsync(context -> CommandDefinition.completedSuggestions(handler.apply(context)));
    }

    /** Installs an asynchronous root suggestion {@code handler}. */
    @NotNull
    public CommandBuilder suggestsAsync(@NotNull SuggestionHandler handler) {
        suggestions = handler;
        return this;
    }

    /** Restricts root availability using {@code rule}. */
    @NotNull
    public CommandBuilder availableIf(@NotNull CommandAvailability rule) {
        availability = rule;
        return this;
    }

    /** Adds a fixed-word subcommand configured by {@code configure}. */
    @NotNull
    public CommandBuilder literal(@NotNull String name, @NotNull Consumer<CommandNodeBuilder> configure) {
        CommandNodeBuilder child = new CommandNodeBuilder(CommandNodeKind.LITERAL, name, null);
        configure.accept(child);
        children.add(child);
        return this;
    }

    /** Adds a typed argument node configured by {@code configure}. */
    @NotNull
    public <T> CommandBuilder argument(
            @NotNull String name, @NotNull ArgumentType<T> type, @NotNull Consumer<CommandNodeBuilder> configure) {
        CommandNodeBuilder child = new CommandNodeBuilder(CommandNodeKind.ARGUMENT, name, type);
        configure.accept(child);
        children.add(child);
        return this;
    }

    /**
     * Adds the whole tree of {@code definition} as the literal {@code name}, so a separate command can live under
     * this one.
     *
     * <p>The mounted node keeps its permission, sender rule, availability, handlers and children. The aliases of
     * {@code definition} are not carried over.
     */
    @NotNull
    public CommandBuilder mount(@NotNull String name, @NotNull CommandDefinition definition) {
        mounted.add(CommandNode.mounted(name, definition));
        return this;
    }

    /** Validates this tree and creates an immutable command definition. */
    @NotNull
    public CommandDefinition build() {
        CommandNodeBuilder root = new CommandNodeBuilder(CommandNodeKind.ROOT, name, null);
        if (permission != null) {
            root.permission(permission, alsoPermissions.toArray(new String[0]));
        }
        if (noPermissionMessage != null) {
            root.noPermissionMessage(noPermissionMessage);
        }
        for (Map.Entry<Class<? extends CommandError>, CommandErrorHandler<?>> entry : errorHandlers.entrySet()) {
            root.putErrorHandler(entry.getKey(), entry.getValue());
        }
        if (senderRule == SenderRule.PLAYER) {
            root.playerOnly();
        } else if (senderRule == SenderRule.CONSOLE) {
            root.consoleOnly();
        }
        root.consoleBypassesPermission(consoleBypassesPermission);
        root.availableIf(availability);
        if (executable) {
            root.executesAsync(execution);
        }
        if (acceptsExtraArguments) {
            root.acceptsExtraArguments();
        }
        root.suggestsAsync(suggestions);
        for (CommandNodeBuilder child : children) {
            root.addBuiltChild(child);
        }
        root.addMounted(mounted);
        return new CommandDefinition(name, aliases, permission, consoleBypassesPermission, execution, suggestions,
                root.build(), noPermissionMessage);
    }
}
