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

    fun product(id: String, configure: Consumer<ProductBuilder>) = apply {
        val builder = ProductBuilder(ProductId.of(id))
        configure.accept(builder)
        val dependency = builder.build()
        require(dependencies.none { it.managed?.product == dependency.product }) {
            "duplicate product dependency: ${dependency.product}"
        }
        dependencies += dependency
    }

    fun plugin(name: String, configure: Consumer<ExternalBuilder>) = apply {
        val builder = ExternalBuilder(name)
        configure.accept(builder)
        val dependency = builder.build()
        require(dependencies.none { it.external?.plugin?.equals(dependency.plugin, true) == true }) {
            "duplicate plugin dependency: ${dependency.plugin}"
        }
        dependencies += dependency
    }

    fun build(): List<PluginDependency> = Collections.unmodifiableList(ArrayList(dependencies))

    class ProductBuilder internal constructor(private val id: ProductId) {
        private var minimum: SemanticVersion? = null
        private var maximumInclusive: SemanticVersion? = null
        private var maximumExclusive: SemanticVersion? = null
        private var repositoryOwner: String? = null
        private var repositoryName: String? = null
        private var required = true
        private var policy = DownloadPolicy.MANUAL

        fun minimumVersion(value: String) = apply { minimum = SemanticVersion.parse(value) }
        fun maximumVersion(value: String) = apply { maximumInclusive = SemanticVersion.parse(value) }
        fun maximumVersionExclusive(value: String) = apply { maximumExclusive = SemanticVersion.parse(value) }
        fun github(owner: String, repository: String) = apply {
            repositoryOwner = owner
            repositoryName = repository
        }
        fun required(value: Boolean) = apply { required = value }
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

    class ExternalBuilder internal constructor(private val name: String) {
        private var minimum: SemanticVersion? = null
        private var maximumInclusive: SemanticVersion? = null
        private var maximumExclusive: SemanticVersion? = null
        private var page: URI? = null
        private var artifact: ExternalArtifact? = null
        private var required = true
        private var policy = DownloadPolicy.MANUAL

        init { require(name.isNotBlank()) { "external plugin name must not be blank" } }

        fun minimumVersion(value: String) = apply { minimum = SemanticVersion.parse(value) }
        fun maximumVersion(value: String) = apply { maximumInclusive = SemanticVersion.parse(value) }
        fun maximumVersionExclusive(value: String) = apply { maximumExclusive = SemanticVersion.parse(value) }
        fun downloadPage(url: String) = apply {
            val parsed = URI.create(url)
            require(parsed.scheme.equals("https", true) && !parsed.host.isNullOrBlank()) {
                "download page must use HTTPS"
            }
            page = parsed
        }
        fun artifact(url: String, size: Long, sha256: String) = apply {
            artifact = ExternalArtifact(URI.create(url), size, sha256)
        }
        fun required(value: Boolean) = apply { required = value }
        fun downloadPolicy(value: DownloadPolicy) = apply { policy = value }

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
                    artifact?.let { target.artifact(it.uri.toString(), it.size, it.sha256) }
                }
                .required(required)
                .downloadPolicy(policy)
                .build()
        }
    }
}
