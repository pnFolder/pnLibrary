package org.pnfolder.pnlibrary.core.runtime.details;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity;
import org.pnfolder.pnlibrary.api.updates.UpdateRegistration;
import org.pnfolder.pnlibrary.api.updates.product.ProductDescriptor;
import org.pnfolder.pnlibrary.api.updates.request.PluginUpdateRequest;
import org.pnfolder.pnlibrary.spi.platform.PlatformIdentity;
import org.pnfolder.pnlibrary.spi.platform.plugin.PlatformOwnership;

/** Registers the library itself with the update service. */
public final class RuntimeUpdateMonitor {
    private RuntimeUpdateMonitor() {
    }

    /** The library's version and the registration that watches for its updates. */
    public static final class Registration {
        private final String currentVersion;
        private final UpdateRegistration monitor;

        public Registration(String currentVersion, UpdateRegistration monitor) {
            this.currentVersion = currentVersion;
            this.monitor = monitor;
        }

        public @NotNull String getCurrentVersion() {
            return currentVersion;
        }

        public @NotNull UpdateRegistration getMonitor() {
            return monitor;
        }
    }

    public static @NotNull Registration register(@NotNull PnLibrary library, @NotNull Object owner, @NotNull PlatformIdentity identity,
                                                 @NotNull PlatformOwnership ownership) {
        String currentVersion = ownership.ownerDetails(owner).get("version");
        if (currentVersion == null || currentVersion.trim().isEmpty()) {
            throw new IllegalStateException("The platform did not expose the pnLibrary version");
        }
        String repository = PnLibraryIdentity.REPOSITORY;
        int slash = repository.indexOf('/');
        UpdateRegistration monitor = library.getUpdates().register(
                owner,
                ProductDescriptor.library(currentVersion),
                PluginUpdateRequest.builder()
                        .repository(repository.substring(0, slash), repository.substring(slash + 1))
                        .artifact("(?i)^" + PnLibraryIdentity.NAME + "-[0-9].*-" + identity.getArtifactId() + "-java[0-9]+\\.jar$", 8)
                        .build());
        return new Registration(currentVersion, monitor);
    }
}
