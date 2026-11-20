package org.pnfolder.pnlibrary.api.dependencies.external

import org.pnfolder.pnlibrary.api.dependencies.DownloadPolicy
import org.pnfolder.pnlibrary.api.dependencies.PluginDependency
import org.pnfolder.pnlibrary.api.dependencies.VersionConstraint
import org.pnfolder.pnlibrary.api.internal.requireHttpsUri
import org.pnfolder.pnlibrary.api.platform.PlatformType
import org.pnfolder.pnlibrary.api.version.SemanticVersion
import java.net.URI

/** Required third-party plugin which may be manual-only or have an exact verified artifact. */
class ExternalPluginDependency private constructor(builder: Builder) : PluginDependency {
    /** Returns this dependency as its external representation. */
    override val external: ExternalPluginDependency get() = this
    /** Third-party plugin name expected from the native platform. */
    val plugin: String = builder.plugin
    /** Short name for people, such as `Vault economy`; the plugin name when not given. */
    val title: String = builder.title ?: builder.plugin
    /** One or two sentences on why the plugin is needed, shown to the administrator when it is missing. */
    val description: String? = builder.description
    /** Oldest accepted plugin version. */
    val minimumVersion: SemanticVersion = builder.minimumVersion
    /** Accepted semantic-version interval. */
    override val versions: VersionConstraint = builder.versions()
    /** Administrator-facing download page, when declared. */
    val downloadPage: URI? = builder.downloadPage
    /** Every downloadable file, specific variants first and the general one last; empty when only a page is given. */
    val artifacts: List<ExternalArtifact> = builder.allArtifacts()
    /** The general downloadable file (or the first variant), when automatic installation is possible. */
    val artifact: ExternalArtifact? = artifacts.firstOrNull { it.platforms.isEmpty() && it.minecraftFrom == null && it.minecraftBefore == null }
        ?: artifacts.firstOrNull()

    /** The file meant for this server: the first variant that fits [platform] and [minecraft], or null when none does. */
    fun artifactFor(platform: PlatformType, minecraft: String?): ExternalArtifact? = artifacts.firstOrNull { it.fits(platform, minecraft) }
    /** Whether staged JAR metadata must match [plugin]. */
    val verifyPluginId: Boolean = builder.verifyPluginId
    /** Whether staged JAR metadata must satisfy [versions]. */
    val verifyVersion: Boolean = builder.verifyVersion
    /** Whether absence blocks the declaring product. */
    override val required: Boolean = builder.required
    /** Administrator/automatic download policy. */
    override val downloadPolicy: DownloadPolicy = builder.downloadPolicy
        ?: if (artifacts.isNotEmpty()) DownloadPolicy.AUTOMATIC else DownloadPolicy.MANUAL

    /** Fluent builder for an [ExternalPluginDependency]. */
    class Builder internal constructor(
        internal val plugin: String,
        internal val minimumVersion: SemanticVersion,
    ) {
        internal var downloadPage: URI? = null
        internal var artifact: ExternalArtifact? = null
        internal var required = true
        internal var maximumInclusive: SemanticVersion? = null
        internal var maximumExclusive: SemanticVersion? = null
        internal var downloadPolicy: DownloadPolicy? = null
        internal val mirrors = mutableListOf<URI>()
        internal var verifyPluginId = true
        internal var verifyVersion = true
        internal val variants = mutableListOf<ExternalArtifact>()
        internal var title: String? = null
        internal var generalMaxBytes: Long? = null
        internal var description: String? = null

        /** Sets the largest accepted size in bytes of the general file (`artifact` or `url`); variants take their own `maxBytes`. */
        fun maxBytes(value: Long): Builder = apply { generalMaxBytes = value }

        /** Sets the short name people read in the notice about the missing plugin. */
        fun title(value: String): Builder = apply { title = value.trim().ifEmpty { null } }

        /** Says why the plugin is needed, in a sentence or two. */
        fun description(value: String): Builder = apply { description = value.trim().ifEmpty { null } }

        /** Sets the page where the administrator can download the plugin by hand. */
        fun downloadPage(url: String): Builder = apply {
            downloadPage = requireHttpsUri(url, "Download page must use HTTPS")
        }

        /** Sets a direct download with the SHA-256 checksum the file must match. */
        fun artifact(url: String, sha256: String): Builder = apply {
            artifact = ExternalArtifact(URI.create(url), sha256)
        }
        /** Sets a direct download URL without an integrity check. */
        fun url(value: String): Builder = apply { artifact = ExternalArtifact(URI.create(value), null) }

        /**
         * Adds a file meant only for some servers: other [platforms] (empty means all) and/or a range of Minecraft versions
         * from [minecraftFrom] (inclusive) up to [minecraftBefore] (exclusive). It has its own [sha256] and [mirrors]; the
         * first variant that fits the server is the one downloaded, and only that one is checked.
         */
        @JvmOverloads
        fun variant(
            url: String,
            sha256: String? = null,
            platforms: Set<PlatformType> = emptySet(),
            minecraftFrom: String? = null,
            minecraftBefore: String? = null,
            mirrors: List<String> = emptyList(),
            label: String? = null,
            maxBytes: Long? = null,
        ): Builder = apply {
            variants += ExternalArtifact(
                URI.create(url), sha256, mirrors.map { requireHttpsUri(it, "Mirror must use HTTPS") },
                platforms, minecraftFrom, minecraftBefore, label, maxBytes,
            )
        }

        internal fun allArtifacts(): List<ExternalArtifact> = variants + listOfNotNull(artifact)

        /** Adds another HTTPS address of the same file; tried when the one before does not answer. */
        fun mirror(value: String): Builder = apply { mirrors += requireHttpsUri(value, "Mirror must use HTTPS") }

        /** Sets whether the plugin refuses to start without this dependency. */
        fun required(value: Boolean): Builder = apply { required = value }

        /** Sets the highest accepted plugin version, inclusive. */
        fun maximumVersion(value: String): Builder = apply { maximumInclusive = SemanticVersion.parse(value) }

        /** Sets the upper version bound, exclusive. */
        fun maximumVersionExclusive(value: String): Builder = apply { maximumExclusive = SemanticVersion.parse(value) }

        /** Sets how a missing or outdated plugin is downloaded. */
        fun downloadPolicy(value: DownloadPolicy): Builder = apply { downloadPolicy = value }

        /** Selects automatic or manual download policy. */
        fun automaticDownload(value: Boolean): Builder = apply {
            downloadPolicy = if (value) DownloadPolicy.AUTOMATIC else DownloadPolicy.MANUAL
        }
        /** Enables or disables staged plugin-ID verification. */
        fun verifyPluginId(value: Boolean): Builder = apply { verifyPluginId = value }

        /** Enables or disables staged plugin-version verification. */
        fun verifyVersion(value: Boolean): Builder = apply { verifyVersion = value }

        /** Enables or disables mandatory automatic installation. */
        fun forceAutomaticDownload(value: Boolean): Builder = apply {
            if (value) downloadPolicy = DownloadPolicy.FORCED
            else if (downloadPolicy == DownloadPolicy.FORCED) downloadPolicy = null
        }

        internal fun versions() = VersionConstraint(minimumVersion, maximumInclusive, maximumExclusive)

        /** Validates and creates the immutable dependency. */
        fun build(): ExternalPluginDependency {
            require(downloadPage != null || artifact != null || variants.isNotEmpty()) { "External dependency requires a download page or artifact" }
            require(artifact != null || variants.isNotEmpty() || downloadPolicy == null || downloadPolicy == DownloadPolicy.MANUAL) {
                "A download page cannot be installed automatically"
            }
            artifact = artifact?.let { ExternalArtifact(it.uri, it.sha256, mirrors.toList(), maxBytes = generalMaxBytes) }
            check(variants.size + (if (artifact != null) 1 else 0) <= MAX_FILES) { "Too many files for one dependency" }
            versions()
            return ExternalPluginDependency(this)
        }
    }

    companion object {
        private const val MAX_FILES = 16

        /** Starts a dependency for [plugin] with the supplied minimum version. */
        @JvmStatic
        fun builder(plugin: String, minimumVersion: String): Builder {
            require(plugin.isNotBlank()) { "External plugin name must not be blank" }
            return Builder(plugin.trim(), SemanticVersion.parse(minimumVersion))
        }
    }
}
