package org.pnfolder.pnlibrary.api.runtime;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Process-wide access point installed by the pnLibrary platform plugin.
 *
 * <p>Consumer plugins should declare pnLibrary as a platform dependency and call {@link #get} only after pnLibrary has
 * loaded. Retaining the returned instance after platform shutdown is unsupported; use {@link #getOrNull} when code may
 * run during bootstrap or teardown.
 */
public final class PnLibraryProvider {
    private static volatile PnLibrary instance;

    private PnLibraryProvider() {
    }

    /** Returns the installed runtime or fails outside its active lifecycle. */
    @NotNull
    public static PnLibrary get() {
        PnLibrary library = instance;
        if (library == null) {
            throw new IllegalStateException("The pnLibrary runtime is not loaded; declare pnLibrary as a platform dependency");
        }
        return library;
    }

    /** Returns the installed runtime, or {@code null} outside its active lifecycle. */
    @Nullable
    public static PnLibrary getOrNull() {
        return instance;
    }

    /** Installs {@code library}; replacing another live runtime is rejected. */
    public static synchronized void install(@NotNull PnLibrary library) {
        if (instance != null && instance != library) {
            throw new IllegalStateException("A pnLibrary runtime is already installed");
        }
        instance = library;
    }

    /** Clears the provider only when {@code library} is the currently installed instance. */
    public static synchronized void clear(@NotNull PnLibrary library) {
        if (instance == library) {
            instance = null;
        }
    }
}
