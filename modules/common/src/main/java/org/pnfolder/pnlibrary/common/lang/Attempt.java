package org.pnfolder.pnlibrary.common.lang;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Outcome of an operation that may fail: either its value or the throwable that stopped it. Returned by
 * {@link Failures#runCatchingNonFatal(java.util.concurrent.Callable)} so an ordinary failure is handled without a
 * try block.
 *
 * @param <T> the type of the value on success
 */
public final class Attempt<T> {
    private final T value;
    private final Throwable error;

    private Attempt(T value, Throwable error) {
        this.value = value;
        this.error = error;
    }

    static <T> Attempt<T> success(T value) {
        return new Attempt<>(value, null);
    }

    static <T> Attempt<T> failure(@NotNull Throwable error) {
        return new Attempt<>(null, error);
    }

    /** Whether the operation returned a value. */
    public boolean isSuccess() {
        return error == null;
    }

    /** Whether the operation threw. */
    public boolean isFailure() {
        return error != null;
    }

    /** The throwable that stopped the operation, or {@code null} when it returned a value. */
    @Nullable
    public Throwable exceptionOrNull() {
        return error;
    }

    /** The value, or {@code null} when the operation failed. */
    @Nullable
    public T getOrNull() {
        return error == null ? value : null;
    }

    /** The value, or {@code fallback} when the operation failed. */
    public T getOrDefault(T fallback) {
        return error == null ? value : fallback;
    }

    /** The value, or the result of {@code onFailure} applied to the throwable. */
    public T getOrElse(@NotNull Function<Throwable, T> onFailure) {
        return error == null ? value : onFailure.apply(error);
    }

    /** Calls {@code onSuccess} or {@code onFailure} and returns its result. */
    public <R> R fold(@NotNull Function<T, R> onSuccess, @NotNull Function<Throwable, R> onFailure) {
        return error == null ? onSuccess.apply(value) : onFailure.apply(error);
    }

    /** Calls {@code action} with the value when the operation succeeded and returns this outcome. */
    @NotNull
    public Attempt<T> onSuccess(@NotNull Consumer<T> action) {
        if (error == null) {
            action.accept(value);
        }
        return this;
    }

    /** Calls {@code action} with the throwable when the operation failed and returns this outcome. */
    @NotNull
    public Attempt<T> onFailure(@NotNull Consumer<Throwable> action) {
        if (error != null) {
            action.accept(error);
        }
        return this;
    }
}
