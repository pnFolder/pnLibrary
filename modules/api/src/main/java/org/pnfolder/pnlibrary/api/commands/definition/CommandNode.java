package org.pnfolder.pnlibrary.api.commands.definition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.commands.error.CommandError;
import org.pnfolder.pnlibrary.api.commands.error.CommandErrorHandler;

/** One immutable route node in a portable command tree. */
public final class CommandNode {
    private static final Pattern NODE_NAME = Pattern.compile("[a-z0-9][a-z0-9:_-]*");
    private static final Pattern ARGUMENT_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_-]*");

    private final CommandNodeKind kind;
    private final String name;
    private final ArgumentType<?> argumentType;
    private final String permission;
    private final boolean consoleBypassesPermission;
    private final CommandAvailability availability;
    private final CommandHandler execution;
    private final SuggestionHandler suggestions;
    private final boolean executable;
    private final List<CommandNode> children;
    private final String noPermissionMessage;
    private final Map<Class<? extends CommandError>, CommandErrorHandler<?>> errorHandlers;
    private final SenderRule senderRule;
    private final List<String> alsoPermissions;
    private final List<String> description;
    private final boolean acceptsExtraArguments;

    CommandNode(
            CommandNodeKind kind,
            String name,
            ArgumentType<?> argumentType,
            String permission,
            boolean consoleBypassesPermission,
            CommandAvailability availability,
            CommandHandler execution,
            SuggestionHandler suggestions,
            boolean executable,
            List<CommandNode> children,
            String noPermissionMessage,
            Map<Class<? extends CommandError>, CommandErrorHandler<?>> errorHandlers,
            SenderRule senderRule,
            List<String> alsoPermissions,
            List<String> description,
            boolean acceptsExtraArguments) {
        this.kind = kind;
        this.name = name;
        this.argumentType = argumentType;
        this.permission = permission;
        this.consoleBypassesPermission = consoleBypassesPermission;
        this.availability = availability;
        this.execution = execution;
        this.suggestions = suggestions;
        this.executable = executable;
        this.children = Collections.unmodifiableList(new ArrayList<>(children));
        this.noPermissionMessage = noPermissionMessage;
        this.errorHandlers = Collections.unmodifiableMap(
                new LinkedHashMap<>(errorHandlers));
        this.senderRule = senderRule;
        this.alsoPermissions = Collections.unmodifiableList(new ArrayList<>(alsoPermissions));
        this.description = Collections.unmodifiableList(new ArrayList<>(description));
        this.acceptsExtraArguments = acceptsExtraArguments;
    }

    /** Structural role of this node. */
    @NotNull public CommandNodeKind getKind() { return kind; }
    /** Literal value, argument name, or root command name. */
    @NotNull public String getName() { return name; }
    /** Parser used by an argument node, or {@code null}. */
    @Nullable public ArgumentType<?> getArgumentType() { return argumentType; }
    /** Optional permission required to enter this node. */
    @Nullable public String getPermission() { return permission; }
    /** Other permissions that let a sender in just as {@link #getPermission()} does. */
    @NotNull public List<String> getAlsoPermissions() { return alsoPermissions; }
    /** Whether the console ignores {@link #getPermission()}. */
    public boolean getConsoleBypassesPermission() { return consoleBypassesPermission; }
    /** Contextual availability rule. */
    @NotNull public CommandAvailability getAvailability() { return availability; }
    /** Execution callback for this route. */
    @NotNull public CommandHandler getExecution() { return execution; }
    /** Suggestion callback for this route. */
    @NotNull public SuggestionHandler getSuggestions() { return suggestions; }
    /** Whether this route may be executed. */
    public boolean isExecutable() { return executable; }
    /** Immutable child routes. */
    @NotNull public List<CommandNode> getChildren() { return children; }
    /** Text shown when the sender lacks the permission, or {@code null} to use the command's own. */
    @Nullable public String getNoPermissionMessage() { return noPermissionMessage; }
    /** Handlers for errors that occur at or below this node, by error class. */
    @NotNull public Map<Class<? extends CommandError>, CommandErrorHandler<?>> getErrorHandlers() { return errorHandlers; }
    /** Who may enter this node. */
    @NotNull public SenderRule getSenderRule() { return senderRule; }
    /** What this node does and how, one line each; shown under the usage line when a sender stops here. */
    @NotNull public List<String> getDescription() { return description; }

    /**
     * Whether an executable node still runs when the sender types words no child matches; the handler then sees every
     * typed word in {@code CommandContext.getArguments()}.
     */
    public boolean getAcceptsExtraArguments() { return acceptsExtraArguments; }

    /**
     * Copies the root of {@code definition} as a literal called {@code name} directly under another command's root.
     *
     * <p>The handlers of every node in the copy see their arguments as if the mounted command stood alone: the literal
     * that leads to it is dropped, so {@code /pn events view abc} reaches the handler as {@code view abc}.
     */
    static CommandNode mounted(String name, CommandDefinition definition) {
        return rebased(definition.getRoot(), normalizeName(name));
    }

    static String normalizeName(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!NODE_NAME.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Command literal must match " + NODE_NAME.pattern());
        }
        return normalized;
    }

    static String normalizeArgumentName(String value) {
        String normalized = value.trim();
        if (!ARGUMENT_NAME.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Command argument name must match " + ARGUMENT_NAME.pattern());
        }
        return normalized;
    }

    private static CommandNode rebased(CommandNode node, String mountName) {
        final CommandHandler execution = node.execution;
        final SuggestionHandler suggestions = node.suggestions;
        List<CommandNode> children = new ArrayList<>();
        for (CommandNode child : node.children) {
            children.add(rebased(child, null));
        }
        return new CommandNode(
                mountName != null ? CommandNodeKind.LITERAL : node.kind,
                mountName != null ? mountName : node.name,
                node.argumentType,
                node.permission,
                node.consoleBypassesPermission,
                node.availability,
                context -> execution.execute(context.withArguments(withoutFirst(context.getArguments()))),
                context -> suggestions.suggest(context.withArguments(withoutFirst(context.getArguments()))),
                node.executable,
                children,
                node.noPermissionMessage,
                node.errorHandlers,
                node.senderRule,
                node.alsoPermissions,
                node.description,
                node.acceptsExtraArguments);
    }

    private static List<String> withoutFirst(List<String> words) {
        return words.isEmpty() ? words : words.subList(1, words.size());
    }
}
