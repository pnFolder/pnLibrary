package org.pnfolder.pnlibrary.api.placeholders.argument;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.placeholders.NoValueException;
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo;

import java.util.function.Function;

/**
 * One part of the address that the method reads: the text as written, and what it became.
 *
 * <p>In an address there is only text. The library turns it into {@code T} with the adapter of that type and hands over
 * this wrapper, so the method still has the text when the conversion failed.
 *
 * @param <T> the type the text becomes
 */
public final class ArgumentValue<T> {
    private final String text;
    private final T value;
    private final String error;
    private final Function<ViewerInfo, T> me;

    /** Creates the wrapper for a type that has no {@code me}. */
    public ArgumentValue(@NotNull String text, @Nullable T value, @Nullable String error) {
        this(text, value, error, null);
    }

    /**
     * Creates the wrapper.
     *
     * @param text what was written in the address
     * @param value the converted value, or {@code null} when the text does not fit
     * @param error why it does not fit, or {@code null}
     * @param me what {@code me} means for a viewer, or {@code null} when it means nothing
     */
    public ArgumentValue(@NotNull String text, @Nullable T value, @Nullable String error, @Nullable Function<ViewerInfo, T> me) {
        this.text = text;
        this.value = value;
        this.error = error;
        this.me = me;
    }

    /** What was written in the address. */
    @NotNull
    public String getText() {
        return text;
    }

    /** The converted value, or {@code null} when the text does not fit. */
    @Nullable
    public T getValue() {
        return value;
    }

    /** Why the text does not fit, or {@code null}. */
    @Nullable
    public String getError() {
        return error;
    }

    /** Whether nothing was written. */
    public boolean isEmpty() {
        return text.isEmpty();
    }

    /** Whether the text is empty or {@code me}: the one who looks. */
    public boolean isMe() {
        return text.isEmpty() || text.equalsIgnoreCase("me");
    }

    /** The value, or {@code defaultValue} when there is none. */
    @NotNull
    public T orElse(@NotNull T defaultValue) {
        return value != null ? value : defaultValue;
    }

    /** The value, or ends the placeholder quietly when there is none. */
    @NotNull
    public T required() {
        if (value == null) {
            throw new NoValueException(error != null ? error : "No value for '" + text + "'");
        }
        return value;
    }

    /** For {@code me} or an empty text the viewer himself, otherwise the value. */
    @Nullable
    public T orMe(@NotNull ViewerInfo viewer) {
        if (!isMe() || me == null) {
            return value;
        }
        T mine = me.apply(viewer);
        return mine != null ? mine : value;
    }

    @NotNull
    @Override
    public String toString() {
        return "ArgumentValue(text=" + text + ", value=" + value + ", error=" + error + ")";
    }
}
