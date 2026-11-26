package org.pnfolder.pnlibrary.api.dependencies;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.dependencies.external.ExternalPluginDependency;
import org.pnfolder.pnlibrary.api.dependencies.external.ManagedProductDependency;
import org.pnfolder.pnlibrary.api.downloads.DownloadPolicy;

/** One dependency accepted by {@code PluginBuilder.depends}. */
public interface PluginDependency {
    /** Whether absence or an outdated version blocks plugin registration. */
    default boolean getRequired() {
        return true;
    }

    /** Accepted dependency-version interval. */
    @NotNull
    VersionConstraint getVersions();

    /** Policy governing whether pnLibrary may download this dependency. */
    @NotNull
    default DownloadPolicy getDownloadPolicy() {
        return DownloadPolicy.MANUAL;
    }

    /** Managed pnLibrary component, when this is a library component dependency. */
    @Nullable
    default ManagedProductDependency getManaged() {
        return null;
    }

    /** Native/third-party plugin dependency, when this is an external dependency. */
    @Nullable
    default ExternalPluginDependency getExternal() {
        return null;
    }
}
