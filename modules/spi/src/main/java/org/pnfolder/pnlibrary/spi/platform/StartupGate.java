package org.pnfolder.pnlibrary.spi.platform;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Holds startup-dependent work until the native entry point reports that the platform is ready.
 *
 * <p>Tasks submitted before {@link #markReady()} are queued and released once, in submission order, through
 * the dispatcher. Later tasks are dispatched immediately. Nothing runs after the lifecycle is closed.
 */
public final class StartupGate {
    private final PlatformLifecycle lifecycle;
    private final Consumer<Runnable> dispatch;
    private final Object lock = new Object();
    private boolean ready;
    private final List<Runnable> pending = new ArrayList<>();

    public StartupGate(@NotNull PlatformLifecycle lifecycle, @NotNull Consumer<Runnable> dispatch) {
        this.lifecycle = lifecycle;
        this.dispatch = dispatch;
    }

    /** Queues {@code task} until the gate opens, or dispatches it immediately when it is already open. */
    public void whenReady(@NotNull Runnable task) {
        synchronized (lock) {
            if (lifecycle.isClosed()) {
                return;
            }
            if (!ready) {
                pending.add(task);
                return;
            }
        }
        dispatch.accept(task);
    }

    /** Opens the gate and releases queued work once; later calls and calls after close are ignored. */
    public void markReady() {
        List<Runnable> released;
        synchronized (lock) {
            if (ready || lifecycle.isClosed()) {
                return;
            }
            ready = true;
            released = new ArrayList<>(pending);
            pending.clear();
        }
        released.forEach(dispatch);
    }

    /** Drops queued work that has not been released yet. */
    public void discardPending() {
        synchronized (lock) {
            pending.clear();
        }
    }
}
