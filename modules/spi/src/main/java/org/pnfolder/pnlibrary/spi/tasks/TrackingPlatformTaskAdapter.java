package org.pnfolder.pnlibrary.spi.tasks;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Shared lifecycle wrapper for native task adapters.
 *
 * <p>Platform implementations only translate a request to a native handle; this class owns
 * closed-state checks, one-shot release, idempotent cancellation, and bulk shutdown.
 */
public abstract class TrackingPlatformTaskAdapter implements PlatformTaskAdapter {
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Set<TrackedHandle> handles = ConcurrentHashMap.newKeySet();

    /** Hands {@code request} to the native scheduler and returns its handle. */
    @NotNull
    protected abstract PlatformTaskHandle scheduleNative(@NotNull PlatformTaskRequest request);

    /** Called for every request after the closed check and before it is scheduled; the default does nothing. */
    protected void beforeSchedule(@NotNull PlatformTaskRequest request) {
    }

    @NotNull
    @Override
    public final PlatformTaskBuilder buildTask() {
        return new PlatformTaskBuilder(this::track);
    }

    private PlatformTaskHandle track(PlatformTaskRequest request) {
        if (closed.get()) {
            throw new IllegalStateException("Platform task adapter is closed");
        }
        beforeSchedule(request);
        AtomicReference<TrackedHandle> reference = new AtomicReference<>();
        AtomicBoolean completed = new AtomicBoolean(false);
        PlatformTaskRequest forwarded = request.withCallback(new TrackedCallback(request, reference, completed));
        TrackedHandle handle = new TrackedHandle(scheduleNative(forwarded));
        reference.set(handle);
        handles.add(handle);
        if (closed.get()) {
            handle.cancel();
        } else if (completed.get()) {
            handle.release();
        }
        return handle;
    }

    @Override
    public final void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        Throwable failure = null;
        for (TrackedHandle handle : new ArrayList<>(handles)) {
            try {
                handle.cancel();
            } catch (Throwable error) {
                if (failure != null) {
                    failure.addSuppressed(error);
                } else {
                    failure = error;
                }
            }
        }
        handles.clear();
        if (failure != null) {
            sneakyThrow(failure);
        }
    }

    // Close must rethrow whatever the first cancellation threw, exactly as it was thrown.
    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void sneakyThrow(Throwable error) throws E {
        throw (E) error;
    }

    private static final class TrackedCallback implements Runnable, RetirableCallback {
        private final PlatformTaskRequest request;
        private final AtomicReference<TrackedHandle> reference;
        private final AtomicBoolean completed;

        TrackedCallback(PlatformTaskRequest request, AtomicReference<TrackedHandle> reference, AtomicBoolean completed) {
            this.request = request;
            this.reference = reference;
            this.completed = completed;
        }

        @Override
        public void run() {
            try {
                request.getCallback().run();
            } finally {
                if (request.getInterval() == null) {
                    retire();
                }
            }
        }

        @Override
        public void retire() {
            completed.set(true);
            TrackedHandle handle = reference.get();
            if (handle != null) {
                handle.release();
            }
        }
    }

    private final class TrackedHandle implements PlatformTaskHandle {
        private final PlatformTaskHandle delegate;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        TrackedHandle(PlatformTaskHandle delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean cancel() {
            if (!cancelled.compareAndSet(false, true)) {
                return false;
            }
            delegate.cancel();
            handles.remove(this);
            return true;
        }

        void release() {
            handles.remove(this);
        }
    }
}
