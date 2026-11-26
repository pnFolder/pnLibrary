package org.pnfolder.pnlibrary.api.commands.error;

import java.util.Collections;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Anything that stops a command: a sender without permission, a wrong argument, a player who is not online, or an
 * error of your own such as {@code class NoMoney extends CommandError}.
 *
 * <p>An error is an ordinary class, so a handler receives it with its real fields instead of a map of strings, and a
 * handler for a base class also answers every error derived from it: a handler for {@link BadUsage} answers a wrong
 * argument, an unknown word and an unfinished command alike, until something more specific is set.
 *
 * <p>Handlers are attached at five levels, and the most specific level that has one wins: a subcommand, a command, a
 * plugin module, the whole plugin, and last the library's standard answer from the library's settings.
 */
public class CommandError {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z0-9_.-]+)}");

    private final String defaultMessage;

    /** An error that uses the standard text of the nearest built-in base class. */
    public CommandError() {
        this(null);
    }

    /**
     * An error with its own default text.
     *
     * @param defaultMessage what the library says when no handler answers, or {@code null} to use the standard text of
     *     the nearest built-in base class. It may contain {@code {name}} placeholders from {@link #placeholders()}.
     */
    public CommandError(@Nullable String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }

    /** What the library says when no handler answers, or {@code null}. */
    @Nullable
    public String getDefaultMessage() {
        return defaultMessage;
    }

    /** Values that fill {@code {name}} in a message, such as {@code price -> 50} for {@code "Нужно {price} монет"}. */
    @NotNull
    public Map<String, Object> placeholders() {
        return Collections.emptyMap();
    }

    /** Fills every {@code {name}} of {@code template} with the placeholder of that name; unknown names stay as written. */
    @NotNull
    public final String format(@NotNull String template) {
        Map<String, Object> values = placeholders();
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            Object value = values.get(matcher.group(1));
            matcher.appendReplacement(result, Matcher.quoteReplacement(value != null ? value.toString() : matcher.group()));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
