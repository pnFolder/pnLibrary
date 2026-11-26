package org.pnfolder.pnlibrary.api.platform;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;

/** Type-safe access to the native platform API implemented by the active runtime. */
public interface PlatformProvider {
    /** Returns the registered implementation for {@code type}, or {@code null} when unavailable. */
    @Nullable
    <T> T get(@NotNull Class<T> type);

    /** Returns the registered implementation or fails with a descriptive message. */
    @NotNull
    <T> T require(@NotNull Class<T> type);

    /**
     * Returns the native platform API implemented by the runtime of {@code library}, for example
     * {@code PlatformProvider.of(library, BukkitPlatform.class)}.
     */
    @NotNull
    static <T> T of(@NotNull PnLibrary library, @NotNull Class<T> type) {
        return library.getPlatforms().require(type);
    }
}
