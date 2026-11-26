package org.pnfolder.pnlibrary.core.runtime;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryConfig;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryProvider;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.core.diagnostics.DiagnosticsRegistry;
import org.pnfolder.pnlibrary.core.runtime.platform.PlatformRuntimeContext;

/**
 * Creates and publishes the single pnLibrary runtime in the current JVM.
 *
 * <p>This bootstrap owns initialization order only: create the implementation, start internal services, install
 * {@link PnLibraryProvider}, then bind the prepared platform context. Repeated calls while that runtime is active return
 * the existing instance; callers should therefore invoke bootstrap only from the native pnLibrary entry point.
 */
public final class PnLibraryLauncher {
    private static PnLibraryImpl instance;
    private static final DiagnosticsRegistry GLOBAL_REGISTRY = new DiagnosticsRegistry();

    private PnLibraryLauncher() {
    }

    public static @NotNull PnLibrary bootstrap(@NotNull Object owner, @NotNull PlatformRuntimeContext platform) {
        return bootstrap(owner, platform, null);
    }

    /**
     * Creates, initializes, publishes, and binds the process-wide runtime.
     *
     * <p>Initialization order is deliberate: internal services start before {@link PnLibraryProvider} is installed, and native
     * commands and listeners bind only after provider installation. If any phase fails, the partially initialized runtime and
     * platform resources are closed before the original exception is rethrown.
     *
     * @param owner native plugin instance that owns the runtime
     * @param platform prepared platform capabilities from the native boundary
     * @param config a ready runtime configuration; {@code null} builds it from the stored settings ({@code data/settings.json})
     * @return the active runtime, which may be an instance created by an earlier call
     */
    public static synchronized @NotNull PnLibrary bootstrap(@NotNull Object owner, @NotNull PlatformRuntimeContext platform,
                                                            @Nullable PnLibraryConfig config) {
        PnLibraryImpl current = instance;
        if (current != null && !current.isClosed()) {
            return current;
        }
        PnLibraryImpl created = new PnLibraryImpl(owner, platform, GLOBAL_REGISTRY, config, PnLibraryLauncher::release);
        instance = created;
        try {
            created.init();
            PnLibraryProvider.install(created);
            platform.getBinding().bind(created);
            return created;
        } catch (Throwable error) {
            Failures.runCatchingNonFatal(() -> {
                created.close();
                return null;
            }).onFailure(error::addSuppressed);
            throw error;
        }
    }

    private static synchronized void release() {
        instance = null;
    }

    public static @NotNull DiagnosticsRegistry globalRegistry() {
        return GLOBAL_REGISTRY;
    }
}
