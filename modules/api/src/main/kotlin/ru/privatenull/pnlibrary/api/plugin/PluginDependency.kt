package ru.privatenull.pnlibrary.api.plugin

import ru.privatenull.pnlibrary.api.updates.ExternalPluginDependency
import ru.privatenull.pnlibrary.api.updates.ManagedProductDependency
import ru.privatenull.pnlibrary.api.version.SemanticVersion

/** Controls whether pnLibrary may stage a missing dependency. */
enum class DownloadPolicy { MANUAL, AUTOMATIC, FORCED }

/** Inclusive lower bound plus one optional upper bound for a dependency version. */
class VersionConstraint(
    val minimum: SemanticVersion,
    val maximumInclusive: SemanticVersion? = null,
    val maximumExclusive: SemanticVersion? = null,
) {
    init {
        require(maximumInclusive == null || maximumExclusive == null) {
            "only one maximum version bound may be declared"
        }
        require(maximumInclusive == null || maximumInclusive >= minimum) {
            "inclusive maximum must be >= minimum"
        }
        require(maximumExclusive == null || maximumExclusive > minimum) {
            "exclusive maximum must be > minimum"
        }
    }

    fun accepts(version: SemanticVersion): Boolean =
        version >= minimum &&
            (maximumInclusive == null || version <= maximumInclusive) &&
            (maximumExclusive == null || version < maximumExclusive)
}

/** One dependency accepted by [PluginBuilder.depends]. */
interface PluginDependency {
    /** Whether absence or an outdated version blocks plugin registration. */
    val required: Boolean get() = true

    val versions: VersionConstraint
    val downloadPolicy: DownloadPolicy get() = DownloadPolicy.MANUAL
    val automaticDownload: Boolean get() = downloadPolicy != DownloadPolicy.MANUAL
    val forceAutomaticDownload: Boolean get() = downloadPolicy == DownloadPolicy.FORCED
    /** Managed pnLibrary component, when this is a library component dependency. */
    val managed: ManagedProductDependency? get() = null

    /** Native/third-party plugin dependency, when this is an external dependency. */
    val external: ExternalPluginDependency? get() = null
}

/** Short factories for the unified dependency DSL. */
object Dependencies {
    @JvmStatic
    @JvmOverloads
    fun managed(component: String, minimumVersion: String, repositoryOwner: String, repositoryName: String,
                required: Boolean = true, automaticDownload: Boolean = false,
                forceAutomaticDownload: Boolean = false): PluginDependency =
        ManagedProductDependency(component, minimumVersion, repositoryOwner, repositoryName, required,
            when { forceAutomaticDownload -> DownloadPolicy.FORCED; automaticDownload -> DownloadPolicy.AUTOMATIC; else -> DownloadPolicy.MANUAL })

    @JvmStatic
    @JvmOverloads
    fun plugin(plugin: String, minimumVersion: String, downloadPage: String,
               required: Boolean = true, automaticDownload: Boolean = false,
               forceAutomaticDownload: Boolean = false): PluginDependency =
        ExternalPluginDependency.builder(plugin, minimumVersion).downloadPage(downloadPage)
            .required(required).downloadPolicy(when { forceAutomaticDownload -> DownloadPolicy.FORCED; automaticDownload -> DownloadPolicy.AUTOMATIC; else -> DownloadPolicy.MANUAL }).build()

    @JvmStatic
    @JvmOverloads
    fun plugin(plugin: String, minimumVersion: String, downloadUrl: String, size: Long, sha256: String,
               required: Boolean = true, automaticDownload: Boolean = false,
               forceAutomaticDownload: Boolean = false): PluginDependency =
        ExternalPluginDependency.builder(plugin, minimumVersion).artifact(downloadUrl, size, sha256)
            .required(required).downloadPolicy(when { forceAutomaticDownload -> DownloadPolicy.FORCED; automaticDownload -> DownloadPolicy.AUTOMATIC; else -> DownloadPolicy.MANUAL }).build()
}
