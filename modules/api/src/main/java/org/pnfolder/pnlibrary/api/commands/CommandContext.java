package org.pnfolder.pnlibrary.api.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/** One portable command invocation or suggestion request. */
public final class CommandContext {
    private final CommandSender sender;
    private final List<String> arguments;
    private final String invokedAlias;
    private final String currentInput;
    private final Map<String, Object> parsedValues;

    /** Context of an invocation that has parsed no argument node yet. */
    public CommandContext(
            @NotNull CommandSender sender,
            @NotNull List<String> arguments,
            @NotNull String invokedAlias,
            @NotNull String currentInput) {
        this(sender, arguments, invokedAlias, currentInput, Collections.emptyMap());
    }

    /**
     * Creates a context.
     *
     * @param sender platform-neutral command sender
     * @param arguments raw tokens following the invoked command alias
     * @param invokedAlias command name or alias used for this invocation
     * @param currentInput complete input available at the current parsing stage
     * @param parsedValues values produced by argument nodes parsed so far
     */
    public CommandContext(
            @NotNull CommandSender sender,
            @NotNull List<String> arguments,
            @NotNull String invokedAlias,
            @NotNull String currentInput,
            @NotNull Map<String, Object> parsedValues) {
        this.sender = Objects.requireNonNull(sender, "sender");
        this.arguments = Collections.unmodifiableList(new ArrayList<>(arguments));
        this.invokedAlias = Objects.requireNonNull(invokedAlias, "invokedAlias");
        this.currentInput = Objects.requireNonNull(currentInput, "currentInput");
        this.parsedValues = Collections.unmodifiableMap(new LinkedHashMap<>(parsedValues));
    }

    /** Platform-neutral command sender. */
    @NotNull
    public CommandSender getSender() {
        return sender;
    }

    /** Raw tokens following the invoked command alias. */
    @NotNull
    public List<String> getArguments() {
        return arguments;
    }

    /** Command name or alias used for this invocation. */
    @NotNull
    public String getInvokedAlias() {
        return invokedAlias;
    }

    /** Complete input available at the current parsing stage. */
    @NotNull
    public String getCurrentInput() {
        return currentInput;
    }

    /** Immutable values produced by argument nodes parsed so far. */
    @NotNull
    public Map<String, Object> getParsedValues() {
        return parsedValues;
    }

    /**
     * Returns a value parsed by the named argument node.
     *
     * @throws IllegalArgumentException when no such argument was parsed
     */
    @NotNull
    @SuppressWarnings("unchecked")
    public <T> T get(@NotNull String name) {
        Object value = parsedValues.get(name);
        if (value == null) {
            throw new IllegalArgumentException("No parsed command argument named '" + name + "'");
        }
        return (T) value;
    }

    /** Returns whether a parsed argument named {@code name} is available. */
    public boolean contains(@NotNull String name) {
        return parsedValues.containsKey(name);
    }

    /** Returns a copy of this context with other raw tokens. */
    @NotNull
    public CommandContext withArguments(@NotNull List<String> arguments) {
        return new CommandContext(sender, arguments, invokedAlias, currentInput, parsedValues);
    }

    /** Returns a copy of this context with other input at the current parsing stage. */
    @NotNull
    public CommandContext withCurrentInput(@NotNull String currentInput) {
        return new CommandContext(sender, arguments, invokedAlias, currentInput, parsedValues);
    }

    /** Returns a copy of this context with other parsed values. */
    @NotNull
    public CommandContext withParsedValues(@NotNull Map<String, Object> parsedValues) {
        return new CommandContext(sender, arguments, invokedAlias, currentInput, parsedValues);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CommandContext)) {
            return false;
        }
        CommandContext that = (CommandContext) other;
        return sender.equals(that.sender)
                && arguments.equals(that.arguments)
                && invokedAlias.equals(that.invokedAlias)
                && currentInput.equals(that.currentInput)
                && parsedValues.equals(that.parsedValues);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sender, arguments, invokedAlias, currentInput, parsedValues);
    }

    @Override
    public String toString() {
        return "CommandContext(sender=" + sender + ", arguments=" + arguments + ", invokedAlias=" + invokedAlias
                + ", currentInput=" + currentInput + ", parsedValues=" + parsedValues + ")";
    }
}
