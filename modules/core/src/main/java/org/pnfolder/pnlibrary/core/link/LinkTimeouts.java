package org.pnfolder.pnlibrary.core.link;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

/**
 * Fires the timeouts of questions sent over the link.
 *
 * <p>A {@link Link} has no close, and the platform makes a new one on every reload, so the thread must go away by
 * itself: it exits after a minute without work, and a timeout cancelled because the answer came is dropped at once.
 */
public final class LinkTimeouts {
    private static final long IDLE_SECONDS = 60L;

    private final ScheduledThreadPoolExecutor timer;

    public LinkTimeouts(@NotNull String threadName) {
        timer = new ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, threadName);
            thread.setDaemon(true);
            return thread;
        });
        timer.setKeepAliveTime(IDLE_SECONDS, TimeUnit.SECONDS);
        timer.allowCoreThreadTimeOut(true);
        timer.setRemoveOnCancelPolicy(true);
    }

    /** Runs {@code action} once after {@code seconds}; cancel the returned future to drop it. */
    public @NotNull ScheduledFuture<?> after(long seconds, @NotNull Runnable action) {
        return timer.schedule(action, seconds, TimeUnit.SECONDS);
    }
}
