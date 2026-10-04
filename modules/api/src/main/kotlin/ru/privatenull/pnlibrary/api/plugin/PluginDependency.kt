package ru.privatenull.pnlibrary.api.plugin

import ru.privatenull.pnlibrary.api.updates.ExternalPluginDependency
import ru.privatenull.pnlibrary.api.updates.ManagedProductDependency
import ru.privatenull.pnlibrary.api.version.SemanticVersion

/** Controls whether pnLibrary may stage a missing dependency. */
enum class DownloadPolicy {
    MANUAL,
    AUTOMATIC,
    FORCED
}

/**
 * Inclusive lower bound plus one optional upper bound for a dependency version.
 *
 * @property minimum oldest accepted version
 * @property maximumInclusive newest accepted version, inclusive
 * @property maximumExclusive newest boundary, exclusive
 */
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

    /** Returns whether [version] lies inside this interval. */
    fun accepts(version: SemanticVersion): Boolean =
        version >= minimum &&
            (maximumInclusive == null || version <= maximumInclusive) &&
            (maximumExclusive == null || version < maximumExclusive)
}

/** One dependency accepted by [PluginBuilder.depends]. */
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

/** Short factories for the unified dependency DSL. */
object Dependencies {
    /** Creates a dependency on a catalog-managed pnLibrary product. */
    @JvmStatic
    @JvmOverloads
    fun managed(
        component: String,
        minimumVersion: String,
        repositoryOwner: String,
        repositoryName: String,
        required: Boolean = true,
        automaticDownload: Boolean = false,
        forceAutomaticDownload: Boolean = false,
    ): PluginDependency = ManagedProductDependency(
        component,
        minimumVersion,
        repositoryOwner,
        repositoryName,
        required,
        downloadPolicy(automaticDownload, forceAutomaticDownload),
    )

    /** Creates a manual third-party plugin dependency with a download page. */
    @JvmStatic
    @JvmOverloads
    fun plugin(
        plugin: String,
        minimumVersion: String,
        downloadPage: String,
        required: Boolean = true,
        automaticDownload: Boolean = false,
        forceAutomaticDownload: Boolean = false,
    ): PluginDependency = ExternalPluginDependency.builder(plugin, minimumVersion)
        .downloadPage(downloadPage)
        .required(required)
        .downloadPolicy(downloadPolicy(automaticDownload, forceAutomaticDownload))
        .build()

    /** Creates a verifiable third-party plugin dependency with an exact artifact. */
    @JvmStatic
    @JvmOverloads
    fun plugin(
        plugin: String,
        minimumVersion: String,
        downloadUrl: String,
        size: Long,
        sha256: String,
        required: Boolean = true,
        automaticDownload: Boolean = false,
        forceAutomaticDownload: Boolean = false,
    ): PluginDependency = ExternalPluginDependency.builder(plugin, minimumVersion)
        .artifact(downloadUrl, size, sha256)
        .required(required)
        .downloadPolicy(downloadPolicy(automaticDownload, forceAutomaticDownload))
        .build()

    private fun downloadPolicy(automatic: Boolean, forced: Boolean): DownloadPolicy = when {
        forced -> DownloadPolicy.FORCED
        automatic -> DownloadPolicy.AUTOMATIC
        else -> DownloadPolicy.MANUAL
    }
}
