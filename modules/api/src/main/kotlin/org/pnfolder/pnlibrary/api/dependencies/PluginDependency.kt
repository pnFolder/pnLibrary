package org.pnfolder.pnlibrary.api.dependencies

import org.pnfolder.pnlibrary.api.dependencies.external.ManagedProductDependency
import org.pnfolder.pnlibrary.api.dependencies.external.ExternalPluginDependency

/** One dependency accepted by `PluginBuilder.depends`. */
interface PluginDependency {
    /** Whether absence or an outdated version blocks plugin registration. */
    val required: Boolean get() = true

    /** Accepted dependency-version interval. */
    val versions: VersionConstraint
    /** Policy governing whether pnLibrary may download this dependency. */
    val downloadPolicy: DownloadPolicy get() = DownloadPolicy.MANUAL
    /** Whether ordinary automatic download is permitted. */
    val automaticDownload: Boolean get() = downloadPolicy != DownloadPolicy.MANUAL
    /** Whether automatic download is mandatory. */
    val forceAutomaticDownload: Boolean get() = downloadPolicy == DownloadPolicy.FORCED
    /** Managed pnLibrary component, when this is a library component dependency. */
    val managed: ManagedProductDependency? get() = null

    /** Native/third-party plugin dependency, when this is an external dependency. */
    val external: ExternalPluginDependency? get() = null
}
