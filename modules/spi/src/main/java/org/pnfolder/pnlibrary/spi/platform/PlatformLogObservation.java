package org.pnfolder.pnlibrary.spi.platform;

import org.jetbrains.annotations.Nullable;

/** Optional native log stream observed by the shared diagnostics runtime. */
public interface PlatformLogObservation {
    /** Starts forwarding native log lines to {@code observer}; {@code null} stops forwarding. */
    default void observeNativeLogs(@Nullable PlatformLogSink observer) {
    }
}
