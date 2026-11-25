package org.pnfolder.pnlibrary.api.dependencies

import org.pnfolder.pnlibrary.api.downloads.DownloadPolicy
import org.pnfolder.pnlibrary.api.dependencies.external.ExternalPluginDependency
import org.pnfolder.pnlibrary.api.dependencies.external.ManagedProductDependency

/** One dependency accepted by `PluginBuilder.depends`. */
interface PluginDependency {
    /** Whether absence or an outdated version blocks plugin registration. */
    val required: Boolean get() = true

    /** Accepted dependency-version interval. */
    val versions: VersionConstraint
    /** Policy governing whether pnLibrary may download this dependency. */
    val downloadPolicy: DownloadPolicy get() = DownloadPolicy.MANUAL
    /** Managed pnLibrary component, when this is a library component dependency. */
    val managed: ManagedProductDependency? get() = null

    /** Native/third-party plugin dependency, when this is an external dependency. */
    val external: ExternalPluginDependency? get() = null
}
