package org.pnfolder.pnlibrary.api.logging;

import java.util.Collections;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Plugin-bound logger that writes to the console and the black box (journal, diagnostics buffer, error report).
 *
 * <p>Every line reaches the black box. To attach structured key-value facts that are stored alongside the message in
 * the journal and appear in support reports, get a logger that carries them with {@link #with(String, Object)}:
 *
 * <pre>{@code
 * logger.warning("Shop is empty");
 * logger.error("Save failed", exception);
 * logger.with("player", player.getName()).with("shop", id).warning("Purchase refused");
 * }</pre>
 */
public interface PnLogger {
    /** Writes one line at {@code level} to the console and the black box. {@code context} is stored as extra facts. */
    void log(@NotNull LogLevel level, @NotNull String message, @Nullable Throwable error,
             @NotNull Map<String, ?> context);

    /** Writes one line at {@code level} without facts. */
    default void log(@NotNull LogLevel level, @NotNull String message) {
        log(level, message, null, Collections.emptyMap());
    }

    /** Writes one line at {@code level} with the failure that explains it. */
    default void log(@NotNull LogLevel level, @NotNull String message, @Nullable Throwable error) {
        log(level, message, error, Collections.emptyMap());
    }

    /** Writes an ordinary line. */
    default void info(@NotNull String message) {
        log(LogLevel.INFO, message);
    }

    /** Writes an ordinary line with the failure that explains it. */
    default void info(@NotNull String message, @Nullable Throwable error) {
        log(LogLevel.INFO, message, error);
    }

    /** Writes a line that something worked. */
    default void success(@NotNull String message) {
        log(LogLevel.SUCCESS, message);
    }

    /** Writes a line about something that is not right but does not stop the plugin. */
    default void warning(@NotNull String message) {
        log(LogLevel.WARNING, message);
    }

    /** Writes a warning with the failure that explains it. */
    default void warning(@NotNull String message, @Nullable Throwable error) {
        log(LogLevel.WARNING, message, error);
    }

    /** Writes a failure of one action. */
    default void error(@NotNull String message) {
        log(LogLevel.ERROR, message);
    }

    /** Writes a failure of one action with its cause. */
    default void error(@NotNull String message, @Nullable Throwable error) {
        log(LogLevel.ERROR, message, error);
    }

    /** Writes a failure that stops the plugin from working; always reaches the black box regardless of retention. */
    default void critical(@NotNull String message) {
        log(LogLevel.CRITICAL, message);
    }

    /** Writes a failure that stops the plugin from working, with its cause; always reaches the black box. */
    default void critical(@NotNull String message, @Nullable Throwable error) {
        log(LogLevel.CRITICAL, message, error);
    }

    /** Writes a detail for a support report only; the console stays quiet. */
    default void debug(@NotNull String message) {
        log(LogLevel.DEBUG, message);
    }

    /** Writes a detail with the failure that explains it, for a support report only. */
    default void debug(@NotNull String message, @Nullable Throwable error) {
        log(LogLevel.DEBUG, message, error);
    }

    /** A logger that adds {@code context} to every line it writes. */
    default @NotNull PnLogger with(@NotNull Map<String, ?> context) {
        return new ContextPnLogger(this, context);
    }

    /** A logger that adds the fact {@code name} = {@code value} to every line it writes; calls add up. */
    default @NotNull PnLogger with(@NotNull String name, @Nullable Object value) {
        return with(Collections.singletonMap(name, value));
    }

    /**
     * Records a line in the black box at {@code level} without printing to the console.
     * Use to replay buffered pre-startup entries after the library has initialised.
     */
    default void record(@NotNull LogLevel level, @NotNull String message, @Nullable Throwable error) {
        debug(message, error);
    }

    /** Records a line without a failure; see {@link #record(LogLevel, String, Throwable)}. */
    default void record(@NotNull LogLevel level, @NotNull String message) {
        record(level, message, null);
    }
}
