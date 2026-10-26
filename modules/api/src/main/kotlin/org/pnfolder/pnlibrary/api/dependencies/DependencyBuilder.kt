package org.pnfolder.pnlibrary.api.dependencies

import org.pnfolder.pnlibrary.api.updates.ProductId
import org.pnfolder.pnlibrary.api.version.SemanticVersion
import java.util.Collections
import java.util.function.Consumer

/** Mutable Java-friendly collector for all dependencies of one module. */
class DependencyBuilder {
    private val dependencies = mutableListOf<PluginDependency>()

    /** Adds a managed product dependency configured under [id]. */
    fun product(id: String, configure: Consumer<ProductBuilder>): DependencyBuilder = apply {
        val builder = ProductBuilder(ProductId.of(id))
        configure.accept(builder)
        val dependency = builder.build()
        require(dependencies.none { it.managed?.product == dependency.product }) {
            "Duplicate product dependency: ${dependency.product}"
        }
        dependencies += dependency
    }

    /** Adds an external native-plugin dependency configured under [name]. */
    fun plugin(name: String, configure: Consumer<ExternalBuilder>): DependencyBuilder = apply {
        val builder = ExternalBuilder(name)
        configure.accept(builder)
        val dependency = builder.build()
        require(dependencies.none { it.external?.plugin?.equals(dependency.plugin, true) == true }) {
            "Duplicate plugin dependency: ${dependency.plugin}"
        }
        dependencies += dependency
    }

    /** Adds an external dependency with [minimumVersion] preconfigured. */
    fun plugin(name: String, minimumVersion: String, configure: Consumer<ExternalBuilder>): DependencyBuilder =
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
        fun minimumVersion(value: String): ProductBuilder = apply { minimum = SemanticVersion.parse(value) }
        /** Sets the inclusive maximum version. */
        fun maximumVersion(value: String): ProductBuilder = apply { maximumInclusive = SemanticVersion.parse(value) }
        /** Sets the exclusive maximum version. */
        fun maximumVersionExclusive(value: String): ProductBuilder = apply { maximumExclusive = SemanticVersion.parse(value) }
        /** Sets the GitHub catalogue repository coordinates. */
        fun github(owner: String, repository: String): ProductBuilder = apply {
            repositoryOwner = owner
            repositoryName = repository
        }
        /** Sets whether absence blocks registration. */
        fun required(value: Boolean): ProductBuilder = apply { required = value }
        /** Sets the dependency download policy. */
        fun downloadPolicy(value: DownloadPolicy): ProductBuilder = apply { policy = value }

        internal fun build(): ManagedProductDependency {
            val versions = VersionConstraint(requireNotNull(minimum) { "Minimum product version is required" },
                maximumInclusive, maximumExclusive)
            return ManagedProductDependency(id, versions,
                requireNotNull(repositoryOwner) { "GitHub repository owner is required" },
                requireNotNull(repositoryName) { "GitHub repository name is required" },
                required, policy)
        }
    }

    /** Fluent builder for one third-party native-plugin dependency. */
    class ExternalBuilder internal constructor(private val name: String) {
        private var minimum: String? = null
        private val settings = mutableListOf<Consumer<ExternalPluginDependency.Builder>>()

        init { require(name.isNotBlank()) { "External plugin name must not be blank" } }

        /** Sets the lowest accepted version; it must be a semantic version. */
        fun minimumVersion(value: String): ExternalBuilder = apply {
            SemanticVersion.parse(value)
            minimum = value
        }
        /** Sets the highest accepted version, inclusive; it must be a semantic version. */
        fun maximumVersion(value: String): ExternalBuilder {
            SemanticVersion.parse(value)
            return add { it.maximumVersion(value) }
        }
        /** Sets the first version that is no longer accepted; it must be a semantic version. */
        fun maximumVersionExclusive(value: String): ExternalBuilder {
            SemanticVersion.parse(value)
            return add { it.maximumVersionExclusive(value) }
        }
        /** Sets an HTTPS page where administrators can obtain the plugin. */
        fun downloadPage(url: String): ExternalBuilder = add { it.downloadPage(url) }
        /** Sets a fully verified artifact suitable for automatic installation. */
        fun artifact(url: String, size: Long, sha256: String): ExternalBuilder = add { it.artifact(url, size, sha256) }
        /** Sets an HTTPS artifact without publisher integrity metadata. */
        fun url(value: String): ExternalBuilder = add { it.url(value) }
        /** Sets whether absence blocks registration. */
        fun required(value: Boolean): ExternalBuilder = add { it.required(value) }
        /** Enables or disables staged plugin-ID verification. */
        fun verifyPluginId(value: Boolean): ExternalBuilder = add { it.verifyPluginId(value) }
        /** Enables or disables staged plugin-version verification. */
        fun verifyVersion(value: Boolean): ExternalBuilder = add { it.verifyVersion(value) }
        /** Sets the dependency download policy. */
        fun downloadPolicy(value: DownloadPolicy): ExternalBuilder = add { it.downloadPolicy(value) }
        /** Sets whether the dependency may be downloaded automatically. */
        fun automaticDownload(value: Boolean): ExternalBuilder = add { it.automaticDownload(value) }
        /** Sets whether automatic download is mandatory. */
        fun forceAutomaticDownload(value: Boolean): ExternalBuilder = add { it.forceAutomaticDownload(value) }

        private fun add(step: Consumer<ExternalPluginDependency.Builder>): ExternalBuilder = apply { settings += step }

        internal fun build(): ExternalPluginDependency {
            val builder = ExternalPluginDependency.builder(
                name,
                requireNotNull(minimum) { "Minimum plugin version is required" },
            )
            settings.forEach { it.accept(builder) }
            return builder.build()
        }
    }
}
