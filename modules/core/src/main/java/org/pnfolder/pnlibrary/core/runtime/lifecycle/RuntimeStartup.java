package org.pnfolder.pnlibrary.core.runtime.lifecycle;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.core.runtime.details.RuntimeUpdateMonitor;
import org.pnfolder.pnlibrary.spi.platform.PlatformIdentity;
import org.pnfolder.pnlibrary.spi.platform.PlatformOwnership;

/** Starts the library instance and registers it for updates. */
public final class RuntimeStartup {
    private RuntimeStartup() {
    }

    /** The running library, its version and the update monitor that watches for a newer one. */
    public static final class Started {
        private final PnLibrary library;
        private final String currentVersion;
        private final AutoCloseable updateMonitor;

        public Started(PnLibrary library, String currentVersion, AutoCloseable updateMonitor) {
            this.library = library;
            this.currentVersion = currentVersion;
            this.updateMonitor = updateMonitor;
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
    }

    public static @NotNull Started start(@NotNull Object owner, @NotNull PnLibrary library, @NotNull PlatformIdentity identity,
                                         @NotNull PlatformOwnership ownership) {
        try {
            RuntimeUpdateMonitor.Registration update = RuntimeUpdateMonitor.register(library, owner, identity, ownership);
            return new Started(library, update.getCurrentVersion(), update.getMonitor());
        } catch (Throwable error) {
            Failures.runCatchingNonFatal(() -> {
                library.close();
                return null;
            }).onFailure(error::addSuppressed);
            throw error;
        }
    }
}
