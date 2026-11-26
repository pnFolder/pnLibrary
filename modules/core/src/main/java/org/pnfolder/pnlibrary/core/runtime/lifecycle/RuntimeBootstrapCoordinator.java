package org.pnfolder.pnlibrary.core.runtime.lifecycle;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.core.runtime.PnLibraryLauncher;
import org.pnfolder.pnlibrary.core.runtime.platform.PlatformRuntimeContext;

/** Creates the library instance and its update monitor, undoing partial work if startup fails. */
public final class RuntimeBootstrapCoordinator {
    private RuntimeBootstrapCoordinator() {
    }

    /** The result of a start: the library, its version, its update monitor and the platform's name for the summary. */
    public static final class Started {
        private final PnLibrary library;
        private final String currentVersion;
        private final AutoCloseable updateMonitor;
        private final String platformName;

        public Started(PnLibrary library, String currentVersion, AutoCloseable updateMonitor, String platformName) {
            this.library = library;
            this.currentVersion = currentVersion;
            this.updateMonitor = updateMonitor;
            this.platformName = platformName;
        }

        public @NotNull PnLibrary getLibrary() {
            return library;
        }

        public @NotNull String getCurrentVersion() {
            return currentVersion;
        }

        public @NotNull AutoCloseable getUpdateMonitor() {
            return updateMonitor;
        }

        public @NotNull String getPlatformName() {
            return platformName;
        }
    }

    public static @NotNull Started start(@NotNull Object owner, @NotNull PlatformRuntimeContext context) {
        PnLibrary library = PnLibraryLauncher.bootstrap(owner, context);
        try {
            RuntimeStartup.Started runtime = RuntimeStartup.start(owner, library, context.getIdentity(), context.getOwnership());
            return new Started(runtime.getLibrary(), runtime.getCurrentVersion(), runtime.getUpdateMonitor(), context.getSummaryName());
        } catch (Throwable error) {
            Failures.runCatchingNonFatal(() -> {
                library.close();
                return null;
            }).onFailure(error::addSuppressed);
            throw error;
        }
    }
}
