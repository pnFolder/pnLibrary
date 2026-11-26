package org.pnfolder.pnlibrary.api.commands.definition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Immutable command metadata and behavior registered through pnLibrary. */
public final class CommandDefinition {
    private static final Pattern COMMAND_NAME = Pattern.compile("[a-z0-9][a-z0-9:_-]*");
    /** An alias may also be spelled in any script, such as a Cyrillic {@code обмен}. */
    static final Pattern ALIAS_NAME = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}:_-]*");

    private final String name;
    private final Set<String> aliases;
    private final String permission;
    private final boolean consoleBypassesPermission;
    private final CommandHandler execution;
    private final SuggestionHandler suggestions;
    private final CommandNode root;
    private final String noPermissionMessage;

    CommandDefinition(
            String name,
            Set<String> aliases,
            String permission,
            boolean consoleBypassesPermission,
            CommandHandler execution,
            SuggestionHandler suggestions,
            CommandNode root,
            String noPermissionMessage) {
        this.name = name;
        this.aliases = Collections.unmodifiableSet(new LinkedHashSet<>(aliases));
        this.permission = permission;
        this.consoleBypassesPermission = consoleBypassesPermission;
        this.execution = execution;
        this.suggestions = suggestions;
        this.root = root;
        this.noPermissionMessage = noPermissionMessage;
    }

    /** Starts a command builder for the root label {@code name}. */
    @NotNull
    public static CommandBuilder builder(@NotNull String name) {
        return new CommandBuilder(name);
    }

    /**
     * Builds a definition for the root label {@code name} configured by {@code configure}.
     *
     * <pre>{@code
     * CommandDefinition.command("ping", c -> c.executes(ctx -> ctx.getSender().sendMessage(Component.text("pong"))));
     * }</pre>
     */
    @NotNull
    public static CommandDefinition command(@NotNull String name, @NotNull Consumer<CommandBuilder> configure) {
        CommandBuilder builder = builder(name);
        configure.accept(builder);
        return builder.build();
    }

    /** Returns an already-completed command execution stage. */
    @NotNull
    public static CompletionStage<Void> completedExecution() {
        return CompletableFuture.completedFuture(null);
    }

    /** Returns an already-completed immutable suggestion stage. */
    @NotNull
    public static CompletionStage<List<String>> completedSuggestions(@NotNull List<String> values) {
        return CompletableFuture.completedFuture(Collections.unmodifiableList(new ArrayList<>(values)));
    }

    /** Normalized primary command name. */
    @NotNull
    public String getName() {
        return name;
    }

    /** Normalized immutable command aliases. */
    @NotNull
    public Set<String> getAliases() {
        return aliases;
    }

    /** Optional root permission. */
    @Nullable
    public String getPermission() {
        return permission;
    }

    /** Whether the console ignores the root permission. */
    public boolean getConsoleBypassesPermission() {
        return consoleBypassesPermission;
    }

    /** Root execution callback. */
    @NotNull
    public CommandHandler getExecution() {
        return execution;
    }

    /** Root suggestion callback. */
    @NotNull
    public SuggestionHandler getSuggestions() {
        return suggestions;
    }

    /** Immutable root of the parsed command tree. */
    @NotNull
    public CommandNode getRoot() {
        return root;
    }

    /** Text shown to a sender who lacks the permission, or {@code null} for the default. */
    @Nullable
    public String getNoPermissionMessage() {
        return noPermissionMessage;
    }

    static String normalizeCommandName(String value, String field, Pattern pattern) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!pattern.matcher(normalized).matches()) {
            throw new IllegalArgumentException(field + " must match " + pattern.pattern());
        }
        return normalized;
    }

    static String normalizeCommandName(String value, String field) {
        return normalizeCommandName(value, field, COMMAND_NAME);
    }
}
