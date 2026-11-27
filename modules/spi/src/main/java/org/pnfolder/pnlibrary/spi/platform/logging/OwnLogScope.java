package org.pnfolder.pnlibrary.spi.platform.logging;

import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Marks the log records the library writes itself, so a log observer does not report them back.
 *
 * <p>The mark belongs to the calling thread and nests: a record is own while any {@link #run} on its thread is active.
 */
public final class OwnLogScope {
    private final ThreadLocal<Boolean> active = ThreadLocal.withInitial(() -> false);

    /** Whether the current thread is inside {@link #run}. */
    public boolean isActive() {
        return active.get();
    }

    /** Runs {@code block} while records produced by this thread are treated as the library's own. */
    public <T> T run(@NotNull Supplier<T> block) {
        boolean previous = active.get();
        active.set(true);
        try {
            return block.get();
        } finally {
            active.set(previous);
        }
    }
}
