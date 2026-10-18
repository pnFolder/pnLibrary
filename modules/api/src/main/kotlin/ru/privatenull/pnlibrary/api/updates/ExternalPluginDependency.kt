package ru.privatenull.pnlibrary.api.updates

import ru.privatenull.pnlibrary.api.plugin.DownloadPolicy
import ru.privatenull.pnlibrary.api.plugin.PluginDependency
import ru.privatenull.pnlibrary.api.plugin.VersionConstraint
import ru.privatenull.pnlibrary.api.version.SemanticVersion
import java.net.URI

/** Required third-party plugin which may be manual-only or have an exact verified artifact. */
class ExternalPluginDependency private constructor(builder: Builder) : PluginDependency {
    /** Returns this dependency as its external representation. */
    override val external: ExternalPluginDependency get() = this
    /** Third-party plugin name expected from the native platform. */
    val plugin: String = builder.plugin
    /** Oldest accepted plugin version. */
    val minimumVersion: SemanticVersion = builder.minimumVersion
    /** Accepted semantic-version interval. */
    override val versions: VersionConstraint = builder.versions()
    /** Administrator-facing download page, when declared. */
    val downloadPage: URI? = builder.downloadPage
    /** Exact downloadable artifact, when automatic installation is possible. */
    val artifact: ExternalArtifact? = builder.artifact
    /** Whether staged JAR metadata must match [plugin]. */
    val verifyPluginId: Boolean = builder.verifyPluginId
    /** Whether staged JAR metadata must satisfy [versions]. */
    val verifyVersion: Boolean = builder.verifyVersion
    /** Whether absence blocks the declaring product. */
    override val required: Boolean = builder.required
    /** Administrator/automatic download policy. */
    override val downloadPolicy: DownloadPolicy = builder.downloadPolicy

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
        internal var downloadPolicy = DownloadPolicy.MANUAL
        internal var verifyPluginId = true
        internal var verifyVersion = true

        /** Sets an HTTPS page where administrators can obtain the plugin. */
        fun downloadPage(url: String) = apply {
            val parsed = URI.create(url)
            require(parsed.scheme.equals("https", true) && !parsed.host.isNullOrBlank()) { "download page must use HTTPS" }
            downloadPage = parsed
        }

        /** Sets a fully verified artifact suitable for automatic installation. */
        fun artifact(url: String, size: Long, sha256: String) = apply {
            artifact = ExternalArtifact(URI.create(url), size, sha256)
        }
        /** Sets an HTTPS artifact without publisher integrity metadata. */
        fun url(value: String) = apply { artifact = ExternalArtifact(URI.create(value), null, null) }

        /** Sets whether absence blocks the declaring product. */
        fun required(value: Boolean) = apply { required = value }

        /** Sets the inclusive maximum accepted plugin version. */
        fun maximumVersion(value: String) = apply { maximumInclusive = SemanticVersion.parse(value) }

        /** Sets the exclusive maximum accepted plugin version. */
        fun maximumVersionExclusive(value: String) = apply { maximumExclusive = SemanticVersion.parse(value) }

        /** Sets the download policy directly. */
        fun downloadPolicy(value: DownloadPolicy) = apply { downloadPolicy = value }

        /** Selects automatic or manual download policy. */
        fun automaticDownload(value: Boolean) = apply {
            downloadPolicy = if (value) DownloadPolicy.AUTOMATIC else DownloadPolicy.MANUAL
        }
        /** Enables or disables staged plugin-ID verification. */
        fun verifyPluginId(value: Boolean) = apply { verifyPluginId = value }

        /** Enables or disables staged plugin-version verification. */
        fun verifyVersion(value: Boolean) = apply { verifyVersion = value }

        /** Enables or disables mandatory automatic installation. */
        fun forceAutomaticDownload(value: Boolean) = apply {
            if (value) downloadPolicy = DownloadPolicy.FORCED
            else if (downloadPolicy == DownloadPolicy.FORCED) downloadPolicy = DownloadPolicy.MANUAL
        }

        internal fun versions() = VersionConstraint(minimumVersion, maximumInclusive, maximumExclusive)

        /** Validates and creates the immutable dependency. */
        fun build(): ExternalPluginDependency {
            require(downloadPage != null || artifact != null) { "external dependency requires a download page or artifact" }
            require(artifact != null || downloadPolicy == DownloadPolicy.MANUAL) {
                "a download page cannot be installed automatically"
            }
            versions()
            return ExternalPluginDependency(this)
        }
    }

    /** Creates external-dependency builders. */
    companion object {
        /** Starts a dependency for [plugin] with the supplied minimum version. */
        @JvmStatic
        fun builder(plugin: String, minimumVersion: String): Builder {
            require(plugin.isNotBlank()) { "external plugin name must not be blank" }
            return Builder(plugin.trim(), SemanticVersion.parse(minimumVersion))
        }
    }
}
