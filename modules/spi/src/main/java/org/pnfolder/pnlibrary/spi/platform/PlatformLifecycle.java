package org.pnfolder.pnlibrary.spi.platform;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Owns the registrations created by one platform adapter.
 *
 * <p>A new adapter is {@code READY}. {@link #bind} enters {@code BINDING} and creates
 * commands or listeners registered through {@link #track}. Successful binding leaves it
 * {@code BOUND}; failed binding closes tracked resources and allows another attempt.
 * {@link #close()} permanently moves either open state to {@code CLOSED}.
 *
 * <p>Mutations share one monitor, so shutdown waits for binding to finish.
 * Resources close in reverse registration order, even if individual cleanup fails.
 * Consumer plugin ownership is managed separately from this adapter lifecycle.
 *
 * <pre>{@code
 * lifecycle.bind(() -> {
 *     lifecycle.track(registerCommand());
 *     return lifecycle.track(registerListener());
 * });
 * // During adapter shutdown:
 * lifecycle.close(() -> detachLogObserver());
 * }</pre>
 */
public final class PlatformLifecycle {
    /** Internal states; transitions are owned exclusively by bind and close. */
    private enum State {
        /** No successful binding yet. Initial state and destination of a failed attempt. */
        READY,
        /** Registration is running. Nested binding and shutdown from its callback are forbidden. */
        BINDING,
        /** Registration succeeded. Resources remain owned until shutdown. */
        BOUND,
        /** Shutdown started. This terminal state rejects new registrations and binding. */
        CLOSED
    }

    private volatile State state = State.READY;
    private final List<AutoCloseable> resources = new ArrayList<>();

    /** Whether shutdown has started; this remains true even if cleanup fails. */
    public boolean isClosed() {
        return state == State.CLOSED;
    }

    /** Rejects work submitted after shutdown. */
    public void checkOpen() {
        if (isClosed()) {
            throw new IllegalStateException("Platform adapter is closed");
        }
    }

    /**
     * Registers adapter resources once, holding the lifecycle monitor throughout {@code block}.
     *
     * <p>On success: READY -> BINDING -> BOUND. On failure, tracked resources are closed
     * and the lifecycle returns to READY so binding can be retried. The original
     * registration error is rethrown with cleanup errors attached as suppressed exceptions.
     * Another thread calling {@link #close()} waits until registration completes.
     *
     * @param block registration code; pass each owned registration to {@link #track}
     * @return the value returned by {@code block}
     * @throws IllegalStateException if binding already started, succeeded, or shutdown started
     */
    public synchronized <T> T bind(@NotNull Supplier<T> block) {
        checkOpen();
        if (state != State.READY) {
            throw new IllegalStateException("Platform adapter binding already started or completed");
        }
        state = State.BINDING;
        try {
            T result = block.get();
            state = State.BOUND;
            return result;
        } catch (Throwable error) {
            try {
                Throwable cleanup = closeTracked();
                if (cleanup != null) {
                    error.addSuppressed(cleanup);
                }
            } finally {
                state = State.READY;
            }
            throw error;
        }
    }

    /**
     * Transfers cleanup ownership of {@code resource} to this lifecycle and returns it unchanged.
     * Resources may be tracked before, during, or after binding, until shutdown starts.
     * They are closed in reverse order on shutdown or when binding fails.
     *
     * @throws IllegalStateException if shutdown already started; ownership is not transferred
     */
    @NotNull
    public synchronized <T extends AutoCloseable> T track(@NotNull T resource) {
        checkOpen();
        resources.add(resource);
        return resource;
    }

    /** Shuts down once; returns false if shutdown already started. */
    public synchronized boolean close() {
        return close(() -> { });
    }

    /**
     * Permanently closes the adapter, releasing resources before running {@code afterClose}.
     * All cleanup actions are attempted. The first failure is thrown, with subsequent
     * failures suppressed. The adapter remains closed even if cleanup fails.
     *
     * @param afterClose final cleanup, executed only on the first shutdown attempt
     * @return true on the first successful shutdown; false if already closed
     * @throws IllegalStateException when called from inside the binding callback
     */
    public synchronized boolean close(@NotNull Runnable afterClose) {
        if (isClosed()) {
            return false;
        }
        if (state == State.BINDING) {
            throw new IllegalStateException("Cannot close the platform adapter inside its binding callback");
        }
        state = State.CLOSED;
        Throwable failure = closeTracked();
        try {
            afterClose.run();
        } catch (Throwable cleanupFailure) {
            if (failure == null) {
                failure = cleanupFailure;
            } else {
                failure.addSuppressed(cleanupFailure);
            }
        }
        if (failure != null) {
            sneakyThrow(failure);
        }
        return true;
    }

    /**
     * Shuts a platform adapter down in the order every platform uses: tracked registrations, then startup
     * work still waiting in {@code startup}, then the native tasks of {@code tasks}, and finally native log observation
     * through {@code stopLogObservation}. Every step is attempted; failures are reported as by {@link #close(Runnable)}.
     *
     * @return true on the first shutdown; false if already closed
     */
    public boolean closeAdapter(
            @Nullable StartupGate startup, @NotNull PlatformTaskAdapter tasks, @NotNull Runnable stopLogObservation) {
        return close(() -> {
            try {
                if (startup != null) {
                    startup.discardPending();
                }
                tasks.close();
            } finally {
                stopLogObservation.run();
            }
        });
    }

    @Nullable
    private Throwable closeTracked() {
        List<Throwable> failures = new ArrayList<>();
        List<AutoCloseable> closing = new ArrayList<>(resources);
        Collections.reverse(closing);
        resources.clear();
        for (AutoCloseable resource : closing) {
            try {
                resource.close();
            } catch (Throwable error) {
                failures.add(error);
            }
        }
        if (failures.isEmpty()) {
            return null;
        }
        Throwable first = failures.get(0);
        for (Throwable other : failures.subList(1, failures.size())) {
            first.addSuppressed(other);
        }
        return first;
    }

    // Close must rethrow whatever cleanup threw, exactly as it was thrown.
    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void sneakyThrow(Throwable error) throws E {
        throw (E) error;
    }
}
