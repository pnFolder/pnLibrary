package org.pnfolder.pnlibrary.api.placeholders.reply;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The result of a placeholder method that has to say what it is: an answer, a way to go on, or nothing.
 * Every {@code @Placeholder} method or property returns a {@code Reply} (or a {@code CompletableFuture} of one); a
 * plain return type is refused when the class is registered.
 *
 * <p>The type tells what the method is: {@code Reply<Integer>} is a value (the address ends), {@code Reply<WarInfo>}
 * is a branch (the address goes on in the {@code @Placeholder} members of {@code WarInfo}).
 *
 * <pre>{@code
 * @Placeholder
 * public Reply<Integer> level(@Viewer ViewerInfo viewer) {
 *     Clan clan = Clans.of(viewer.getPlayerId());
 *     return clan == null ? Reply.none() : Reply.value(clan.getLevel());
 * }
 * }</pre>
 *
 * @param <T> the type of the value, or of the object the address goes on with
 */
public final class Reply<T> {
    private final ReplyKind kind;
    private final Object content;
    private final String format;

    private Reply(ReplyKind kind, @Nullable Object content, @Nullable String format) {
        this.kind = kind;
        this.content = content;
        this.format = format;
    }

    /** What the reply is. */
    @NotNull
    public ReplyKind getKind() {
        return kind;
    }

    /** The value, the object to go on with, or the answer; {@code null} for {@link ReplyKind#NONE}. */
    @Nullable
    public Object getContent() {
        return content;
    }

    /** The format applied to the value when the address names none, or {@code null}. */
    @Nullable
    public String getFormat() {
        return format;
    }

    /** An answer. */
    @NotNull
    public static <T> Reply<T> value(T value) {
        return new Reply<>(ReplyKind.VALUE, value, null);
    }

    /** An answer; {@code format} is applied when the address names no format of its own: {@code Reply.value(1250, "coins")}. */
    @NotNull
    public static <T> Reply<T> value(T value, @Nullable String format) {
        return new Reply<>(ReplyKind.VALUE, value, format);
    }

    /** An answer when {@code value} is there, otherwise {@link #none}. For values that may be missing. */
    @NotNull
    public static <T> Reply<T> of(@Nullable T value) {
        return value == null ? new Reply<T>(ReplyKind.NONE, null, null) : new Reply<T>(ReplyKind.VALUE, value, null);
    }

    /** An object to go on with: the address continues in its {@code @Placeholder} members. {@code null} means no value. */
    @NotNull
    public static <T> Reply<T> next(@Nullable T next) {
        return next == null ? new Reply<T>(ReplyKind.NONE, null, null) : new Reply<T>(ReplyKind.NEXT, next, null);
    }

    /** An answer given from a branch: the address ends here with {@code answer} and nothing below is called. */
    @NotNull
    public static <T> Reply<T> stop(@Nullable Object answer) {
        return stop(answer, null);
    }

    /** Like {@link #stop(Object)}, with a {@code format} applied when the address names none. */
    @NotNull
    public static <T> Reply<T> stop(@Nullable Object answer, @Nullable String format) {
        return answer == null ? new Reply<T>(ReplyKind.NONE, null, null) : new Reply<T>(ReplyKind.STOP, answer, format);
    }

    /** No value: the address stays as it was written. */
    @NotNull
    public static <T> Reply<T> none() {
        return new Reply<>(ReplyKind.NONE, null, null);
    }

    @NotNull
    @Override
    public String toString() {
        return "Reply(" + kind + ", " + content + ")";
    }
}
