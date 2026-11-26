package org.pnfolder.pnlibrary.core.runtime.platform;

import org.jetbrains.annotations.NotNull;

/** Registers a platform adapter implementation and returns a handle that unregisters it. */
public interface PlatformRegistration {
    @NotNull <T> AutoCloseable registerPlatform(@NotNull Class<T> type, @NotNull T implementation);
}
