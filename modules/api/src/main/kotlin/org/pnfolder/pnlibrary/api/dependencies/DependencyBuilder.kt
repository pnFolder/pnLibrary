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

        fun minimumVersion(value: String): ExternalBuilder = apply {
            SemanticVersion.parse(value)
            minimum = value
        }
        fun maximumVersion(value: String): ExternalBuilder {
            SemanticVersion.parse(value)
            return add { it.maximumVersion(value) }
        }
        fun maximumVersionExclusive(value: String): ExternalBuilder {
            SemanticVersion.parse(value)
            return add { it.maximumVersionExclusive(value) }
        }
        fun downloadPage(url: String): ExternalBuilder = add { it.downloadPage(url) }
        fun artifact(url: String, size: Long, sha256: String): ExternalBuilder = add { it.artifact(url, size, sha256) }
        fun url(value: String): ExternalBuilder = add { it.url(value) }
        fun required(value: Boolean): ExternalBuilder = add { it.required(value) }
        fun verifyPluginId(value: Boolean): ExternalBuilder = add { it.verifyPluginId(value) }
        fun verifyVersion(value: Boolean): ExternalBuilder = add { it.verifyVersion(value) }
        fun downloadPolicy(value: DownloadPolicy): ExternalBuilder = add { it.downloadPolicy(value) }
        fun automaticDownload(value: Boolean): ExternalBuilder = add { it.automaticDownload(value) }
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
