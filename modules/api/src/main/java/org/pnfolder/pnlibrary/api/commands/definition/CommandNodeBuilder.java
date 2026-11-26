package org.pnfolder.pnlibrary.api.commands.definition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
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

/** Builder shared by literal and argument nodes. */
public final class CommandNodeBuilder {
    private final CommandNodeKind kind;
    private final String name;
    private final ArgumentType<?> argumentType;
    private String permission;
    private List<String> alsoPermissions = Collections.emptyList();
    private boolean consoleBypassesPermission;
    private String noPermissionMessage;
    private final Map<Class<? extends CommandError>, CommandErrorHandler<?>> errorHandlers =
            new LinkedHashMap<>();
    private SenderRule senderRule = SenderRule.ANY;
    private List<String> description = Collections.emptyList();
    private CommandAvailability availability = context -> true;
    private CommandHandler execution = context -> CommandDefinition.completedExecution();
    private SuggestionHandler suggestions = context -> CommandDefinition.completedSuggestions(Collections.emptyList());
    private boolean executable;
    private boolean acceptsExtraArguments;
    private final List<CommandNodeBuilder> children = new ArrayList<>();
    private final List<CommandNode> mounted = new ArrayList<>();

    CommandNodeBuilder(CommandNodeKind kind, String name, ArgumentType<?> argumentType) {
        this.kind = kind;
        this.argumentType = argumentType;
        switch (kind) {
            case ROOT:
                this.name = name;
                break;
            case LITERAL:
                this.name = CommandNode.normalizeName(name);
                break;
            default:
                this.name = CommandNode.normalizeArgumentName(name);
                break;
        }
    }

    /** Requires the non-blank permission {@code value} for this node. */
    @NotNull
    public CommandNodeBuilder permission(@NotNull String value) {
        return permission(value, new String[0]);
    }

    /**
     * Requires the non-blank permission {@code value} for this node. With {@code others} a sender needs only one of
     * them, so a narrow permission such as {@code pnlibrary.updates.download} can open a node next to the wide
     * {@code pnlibrary.admin}.
     */
    @NotNull
    public CommandNodeBuilder permission(@NotNull String value, @NotNull String... others) {
        permission = CommandBuilder.nonBlank(value, "Command permission must not be blank");
        List<String> also = new ArrayList<>();
        for (String other : others) {
            also.add(CommandBuilder.nonBlank(other, "Command permission must not be blank"));
        }
        alsoPermissions = also;
        return this;
    }

    /** Sets the message shown to a sender who lacks this node's permission; it overrides the command's own text. */
    @NotNull
    public CommandNodeBuilder noPermissionMessage(@NotNull String value) {
        noPermissionMessage = CommandBuilder.nonBlank(value, "No-permission message must not be blank");
        return this;
    }

    /** Answers errors of class {@code type} and its subclasses with {@code handler}; see {@link CommandBuilder#onError}. */
    @NotNull
    public <E extends CommandError> CommandNodeBuilder onError(
            @NotNull Class<E> type, @NotNull CommandErrorHandler<E> handler) {
        errorHandlers.put(type, handler);
        return this;
    }

    /** Answers errors of class {@code type} with the plain {@code message}; {@code {name}} fills in from the error. */
    @NotNull
    public <E extends CommandError> CommandNodeBuilder onError(@NotNull Class<E> type, @NotNull String message) {
        final String text = CommandBuilder.nonBlank(message, "Error message must not be blank");
        errorHandlers.put(type, (CommandErrorHandler<E>) (context, error) ->
                context.getSender().sendMessage(Component.text(error.format(text))));
        return this;
    }

    /** Says in a few lines what this node does and how it works; a sender who stops here is shown them under the usage. */
    @NotNull
    public CommandNodeBuilder description(@NotNull String... lines) {
        List<String> kept = new ArrayList<>();
        for (String line : lines) {
            if (!line.trim().isEmpty()) {
                kept.add(line.trim());
            }
        }
        description = kept;
        return this;
    }

    /** Lets only players enter this node; the console gets the {@code PlayerOnly} answer. */
    @NotNull
    public CommandNodeBuilder playerOnly() {
        senderRule = SenderRule.PLAYER;
        return this;
    }

    /** Lets only the console enter this node; players get the {@code ConsoleOnly} answer. */
    @NotNull
    public CommandNodeBuilder consoleOnly() {
        senderRule = SenderRule.CONSOLE;
        return this;
    }

    /** Lets the console ignore this node's permission. */
    @NotNull
    public CommandNodeBuilder consoleBypassesPermission() {
        return consoleBypassesPermission(true);
    }

    /** Configures whether the console bypasses this node's permission. */
    @NotNull
    public CommandNodeBuilder consoleBypassesPermission(boolean enabled) {
        consoleBypassesPermission = enabled;
        return this;
    }

    /** Restricts node availability using {@code rule}. */
    @NotNull
    public CommandNodeBuilder availableIf(@NotNull CommandAvailability rule) {
        availability = rule;
        return this;
    }

    /**
     * Lets this executable node run when the sender types words that none of its children match, instead of answering
     * with an unknown-argument error. The handler reads the words from {@code CommandContext.getArguments()}. A modern
     * Paper client is told the node takes free text, so it does not reject them early.
     */
    @NotNull
    public CommandNodeBuilder acceptsExtraArguments() {
        acceptsExtraArguments = true;
        return this;
    }

    /** Installs a synchronous execution {@code handler}. */
    @NotNull
    public CommandNodeBuilder executes(@NotNull final Consumer<CommandContext> handler) {
        return executesAsync(context -> {
            handler.accept(context);
            return CommandDefinition.completedExecution();
        });
    }

    /** Installs an asynchronous execution {@code handler}. */
    @NotNull
    public CommandNodeBuilder executesAsync(@NotNull CommandHandler handler) {
        executable = true;
        execution = handler;
        return this;
    }

    /** Installs a synchronous suggestion {@code handler}. */
    @NotNull
    public CommandNodeBuilder suggests(@NotNull final Function<CommandContext, List<String>> handler) {
        return suggestsAsync(context -> CommandDefinition.completedSuggestions(handler.apply(context)));
    }

    /** Installs an asynchronous suggestion {@code handler}. */
    @NotNull
    public CommandNodeBuilder suggestsAsync(@NotNull SuggestionHandler handler) {
        suggestions = handler;
        return this;
    }

    /** Adds a fixed-word child node configured by {@code configure}. */
    @NotNull
    public CommandNodeBuilder literal(@NotNull String name, @NotNull Consumer<CommandNodeBuilder> configure) {
        CommandNodeBuilder child = new CommandNodeBuilder(CommandNodeKind.LITERAL, name, null);
        configure.accept(child);
        children.add(child);
        return this;
    }

    /** Adds a typed argument child node configured by {@code configure}. */
    @NotNull
    public <T> CommandNodeBuilder argument(
            @NotNull String name, @NotNull ArgumentType<T> type, @NotNull Consumer<CommandNodeBuilder> configure) {
        CommandNodeBuilder child = new CommandNodeBuilder(CommandNodeKind.ARGUMENT, name, type);
        configure.accept(child);
        children.add(child);
        return this;
    }

    void putErrorHandler(Class<? extends CommandError> type, CommandErrorHandler<?> handler) {
        errorHandlers.put(type, handler);
    }

    void addBuiltChild(CommandNodeBuilder child) {
        children.add(child);
    }

    void addMounted(List<CommandNode> nodes) {
        mounted.addAll(nodes);
    }

    CommandNode build() {
        validateChildren();
        List<CommandNode> built = new ArrayList<>();
        for (CommandNodeBuilder child : children) {
            built.add(child.build());
        }
        built.addAll(mounted);
        return new CommandNode(kind, name, argumentType, permission, consoleBypassesPermission, availability,
                execution, suggestions, executable, built, noPermissionMessage, errorHandlers, senderRule,
                alsoPermissions, description, acceptsExtraArguments);
    }

    private void validateChildren() {
        Set<String> seen = new HashSet<>();
        int arguments = 0;
        for (CommandNodeBuilder child : children) {
            requireUnique(seen, child.kind, child.name);
            if (child.kind == CommandNodeKind.ARGUMENT) {
                arguments++;
            }
        }
        for (CommandNode node : mounted) {
            requireUnique(seen, node.getKind(), node.getName());
        }
        if (arguments > 1) {
            throw new IllegalArgumentException("A command node cannot have multiple argument children");
        }
        for (CommandNodeBuilder child : children) {
            boolean greedy = child.argumentType != null && child.argumentType.getSpec().getKind() == ArgumentSpec.Kind.GREEDY;
            if (greedy && !child.children.isEmpty()) {
                throw new IllegalArgumentException("Greedy argument '" + child.name + "' must be the last node of its branch");
            }
        }
    }

    private static void requireUnique(Set<String> seen, CommandNodeKind kind, String name) {
        if (!seen.add(kind + ":" + name)) {
            throw new IllegalArgumentException("Duplicate command child '" + name + "'");
        }
    }
}
