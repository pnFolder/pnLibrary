package ru.privatenull.pnlibrary.api.plugin

import ru.privatenull.pnlibrary.api.updates.ExternalArtifact
import ru.privatenull.pnlibrary.api.updates.ExternalPluginDependency
import ru.privatenull.pnlibrary.api.updates.ManagedProductDependency
import ru.privatenull.pnlibrary.api.updates.ProductId
import ru.privatenull.pnlibrary.api.version.SemanticVersion
import java.net.URI
import java.util.Collections
import java.util.function.Consumer

/** Mutable Java-friendly collector for all dependencies of one module. */
class DependencyBuilder {
    private val dependencies = mutableListOf<PluginDependency>()

    /** Adds a managed product dependency configured under [id]. */
    fun product(id: String, configure: Consumer<ProductBuilder>) = apply {
        val builder = ProductBuilder(ProductId.of(id))
        configure.accept(builder)
        val dependency = builder.build()
        require(dependencies.none { it.managed?.product == dependency.product }) {
            "duplicate product dependency: ${dependency.product}"
        }
        dependencies += dependency
    }

    /** Adds an external native-plugin dependency configured under [name]. */
    fun plugin(name: String, configure: Consumer<ExternalBuilder>) = apply {
        val builder = ExternalBuilder(name)
        configure.accept(builder)
        val dependency = builder.build()
        require(dependencies.none { it.external?.plugin?.equals(dependency.plugin, true) == true }) {
            "duplicate plugin dependency: ${dependency.plugin}"
        }
        dependencies += dependency
    }

    /** Adds an external dependency with [minimumVersion] preconfigured. */
    fun plugin(name: String, minimumVersion: String, configure: Consumer<ExternalBuilder>) =
        plugin(name) { builder ->
            builder.minimumVersion(minimumVersion)
            configure.accept(builder)
        }

    /** Returns an immutable snapshot of all validated dependencies. */
    fun build(): List<PluginDependency> = Collections.unmodifiableList(ArrayList(dependencies))

    /** Fluent builder for one managed product dependency. */
    class ProductBuilder internal constructor(private val id: ProductId) {
        private var minimum: SemanticVersion? = null
        private var maximumInclusive: SemanticVersion? = null
        private var maximumExclusive: SemanticVersion? = null
        private var repositoryOwner: String? = null
        private var repositoryName: String? = null
        private var required = true
        private var policy = DownloadPolicy.MANUAL

        /** Sets the required minimum version. */
        fun minimumVersion(value: String) = apply { minimum = SemanticVersion.parse(value) }
        /** Sets the inclusive maximum version. */
        fun maximumVersion(value: String) = apply { maximumInclusive = SemanticVersion.parse(value) }
        /** Sets the exclusive maximum version. */
        fun maximumVersionExclusive(value: String) = apply { maximumExclusive = SemanticVersion.parse(value) }
        /** Sets the GitHub catalogue repository coordinates. */
        fun github(owner: String, repository: String) = apply {
            repositoryOwner = owner
            repositoryName = repository
        }
        /** Sets whether absence blocks registration. */
        fun required(value: Boolean) = apply { required = value }
        /** Sets the dependency download policy. */
        fun downloadPolicy(value: DownloadPolicy) = apply { policy = value }

        internal fun build(): ManagedProductDependency {
            val versions = VersionConstraint(requireNotNull(minimum) { "minimum product version is required" },
                maximumInclusive, maximumExclusive)
            return ManagedProductDependency(id, versions,
                requireNotNull(repositoryOwner) { "GitHub repository owner is required" },
                requireNotNull(repositoryName) { "GitHub repository name is required" },
                required, policy)
        }
    }

    /** Fluent builder for one third-party native-plugin dependency. */
    class ExternalBuilder internal constructor(private val name: String) {
        private var minimum: SemanticVersion? = null
        private var maximumInclusive: SemanticVersion? = null
        private var maximumExclusive: SemanticVersion? = null
        private var page: URI? = null
        private var artifact: ExternalArtifact? = null
        private var required = true
        private var policy = DownloadPolicy.MANUAL
        private var verifyPluginId = true
        private var verifyVersion = true

        init { require(name.isNotBlank()) { "external plugin name must not be blank" } }

        /** Sets the required minimum version. */
        fun minimumVersion(value: String) = apply { minimum = SemanticVersion.parse(value) }
        /** Sets the inclusive maximum version. */
        fun maximumVersion(value: String) = apply { maximumInclusive = SemanticVersion.parse(value) }
        /** Sets the exclusive maximum version. */
        fun maximumVersionExclusive(value: String) = apply { maximumExclusive = SemanticVersion.parse(value) }
        /** Sets the administrator-facing HTTPS download page. */
        fun downloadPage(url: String) = apply {
            val parsed = URI.create(url)
            require(parsed.scheme.equals("https", true) && !parsed.host.isNullOrBlank()) {
                "download page must use HTTPS"
            }
            page = parsed
        }
        /** Sets an exact verified artifact suitable for automatic installation. */
        fun artifact(url: String, size: Long, sha256: String) = apply {
            artifact = ExternalArtifact(URI.create(url), size, sha256)
        }
        /** Sets an HTTPS artifact without publisher integrity metadata. */
        fun url(value: String) = apply { artifact = ExternalArtifact(URI.create(value), null, null) }
        /** Sets whether absence blocks registration. */
        fun required(value: Boolean) = apply { required = value }
        /** Enables or disables staged plugin-ID verification. */
        fun verifyPluginId(value: Boolean) = apply { verifyPluginId = value }
        /** Enables or disables staged plugin-version verification. */
        fun verifyVersion(value: Boolean) = apply { verifyVersion = value }
        /** Sets the dependency download policy. */
        fun downloadPolicy(value: DownloadPolicy) = apply { policy = value }
        /** Selects automatic or manual installation. */
        fun automaticDownload(value: Boolean) = apply {
            policy = if (value) DownloadPolicy.AUTOMATIC else DownloadPolicy.MANUAL
        }
        /** Enables or disables mandatory automatic installation. */
        fun forceAutomaticDownload(value: Boolean) = apply {
            if (value) policy = DownloadPolicy.FORCED
            else if (policy == DownloadPolicy.FORCED) policy = DownloadPolicy.MANUAL
        }

        internal fun build(): ExternalPluginDependency {
            val minimumVersion = requireNotNull(minimum) { "minimum plugin version is required" }
            VersionConstraint(minimumVersion, maximumInclusive, maximumExclusive)
            require(page != null || artifact != null) { "external dependency requires a download page or artifact" }
            require(artifact != null || policy == DownloadPolicy.MANUAL) {
                "a download page cannot be installed automatically"
            }
            return ExternalPluginDependency.builder(name.trim(), minimumVersion.toString())
                .also { target ->
                    maximumInclusive?.let { target.maximumVersion(it.toString()) }
                    maximumExclusive?.let { target.maximumVersionExclusive(it.toString()) }
                    page?.let { target.downloadPage(it.toString()) }
                    artifact?.let {
                        if (it.size != null && it.sha256 != null) target.artifact(it.uri.toString(), it.size, it.sha256)
                        else target.url(it.uri.toString())
                    }
                }
                .required(required)
                .verifyPluginId(verifyPluginId)
                .verifyVersion(verifyVersion)
                .downloadPolicy(policy)
                .build()
        }
    }
}
