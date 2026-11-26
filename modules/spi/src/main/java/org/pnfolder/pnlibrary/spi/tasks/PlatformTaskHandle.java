package org.pnfolder.pnlibrary.spi.tasks;

/** Native cancellation handle returned by a platform scheduler. */
@FunctionalInterface
public interface PlatformTaskHandle {
    /** Cancels future execution and reports whether cancellation changed state. */
    boolean cancel();
}
