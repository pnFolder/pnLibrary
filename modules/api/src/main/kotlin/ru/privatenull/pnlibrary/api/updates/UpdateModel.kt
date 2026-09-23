package ru.privatenull.pnlibrary.api.updates

import ru.privatenull.pnlibrary.api.version.ApiVersionRange
import ru.privatenull.pnlibrary.api.version.PnLibraryApi
import ru.privatenull.pnlibrary.api.version.SemanticVersion
import ru.privatenull.pnlibrary.api.platform.PlatformType
import java.net.URI
import java.util.Collections
import java.util.UUID
import ru.privatenull.pnlibrary.api.plugin.PluginDependency
import ru.privatenull.pnlibrary.api.plugin.DownloadPolicy
import ru.privatenull.pnlibrary.api.plugin.VersionConstraint

/** Stable normalized identity of a pnLibrary-managed product. */
class ProductId private constructor(val value: String) : Comparable<ProductId> {
    override fun compareTo(other: ProductId): Int = value.compareTo(other.value)
    override fun equals(other: Any?): Boolean = other is ProductId && value == other.value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = value

    companion object {
        private val VALID = Regex("[a-z0-9][a-z0-9_.-]*")

        @JvmStatic
        fun of(value: String): ProductId {
            val normalized = value.trim().lowercase()
            require(VALID.matches(normalized)) { "Invalid product ID: $value" }
            return ProductId(normalized)
        }
    }
}

/** Minimum semantic version required from another managed component. */
data class ProductDependency(
    val product: ProductId,
    val minimumVersion: SemanticVersion,
)

/** Installed component state used as the starting point for resolution. */
data class InstalledProduct(
    val product: ProductId,
    val version: SemanticVersion,
    val supportedApi: ApiVersionRange,
    val providesApi: Int? = null,
) {
    init {
        require(providesApi == null || providesApi > 0) { "provided API generation must be positive" }
    }
}

/** Normalized release metadata available from a release source. */
data class ProductRelease(
    val product: ProductId,
    val version: SemanticVersion,
    val channel: UpdateChannel,
    val supportedApi: ApiVersionRange,
    val providesApi: Int? = null,
    val dependencies: List<ProductDependency> = emptyList(),
    val repository: String? = null,
    val artifacts: List<ArtifactDescriptor> = emptyList(),
    val externalPluginDependencies: List<ExternalPluginDependency> = emptyList(),
) {
    init {
        require(providesApi == null || providesApi > 0) { "provided API generation must be positive" }
        require(dependencies.map(ProductDependency::product).distinct().size == dependencies.size) {
            "product release contains duplicate dependencies"
        }
    }
}

/** One version transition selected by the resolver. */
data class ProductChange(
    val product: ProductId,
    val from: SemanticVersion?,
    val to: SemanticVersion,
)

/** Immutable atomic update target. */
data class UpdatePlan(
    val targetApi: Int,
    val changes: List<ProductChange>,
    val selected: List<ProductRelease>,
) {
    init {
        require(targetApi > 0) { "target API generation must be positive" }
    }
}

/** Structured explanation for a candidate or complete plan that cannot be installed. */
sealed class BlockedReason {
    data class ApiMismatch(
        val product: ProductId,
        val supportedApi: ApiVersionRange,
        val requiredApi: Int,
        val repository: String? = null,
    ) : BlockedReason()

    data class MissingDependency(
        val product: ProductId,
        val dependency: ProductId,
        val minimumVersion: SemanticVersion,
    ) : BlockedReason()

    data class MissingExternalPluginDependency(
        val product: ProductId,
        val plugin: String,
        val minimumVersion: SemanticVersion,
        val downloadPage: String?,
    ) : BlockedReason()

    data class Frozen(val product: ProductId) : BlockedReason()
    data class NoCompatibleRelease(val product: ProductId, val requiredApi: Int) : BlockedReason()
}

/** A managed component dependency discoverable through a release catalogue. */
class ManagedProductDependency(
    val product: ProductId,
    override val versions: VersionConstraint,
    val repositoryOwner: String,
    val repositoryName: String,
    override val required: Boolean = true,
    override val downloadPolicy: DownloadPolicy = DownloadPolicy.MANUAL,
) : PluginDependency {
    val minimumVersion: SemanticVersion get() = versions.minimum

    constructor(product: ProductId, minimumVersion: SemanticVersion, repositoryOwner: String, repositoryName: String,
                required: Boolean = true, downloadPolicy: DownloadPolicy = DownloadPolicy.MANUAL) : this(
        product, VersionConstraint(minimumVersion), repositoryOwner, repositoryName, required, downloadPolicy,
    )

    constructor(component: String, minimumVersion: String, repositoryOwner: String, repositoryName: String,
                required: Boolean = true, downloadPolicy: DownloadPolicy = DownloadPolicy.MANUAL) : this(
        ProductId.of(component), VersionConstraint(SemanticVersion.parse(minimumVersion)), repositoryOwner, repositoryName,
        required, downloadPolicy,
    )
    override val managed: ManagedProductDependency get() = this
    init {
        require(REPOSITORY_PART.matches(repositoryOwner)) { "invalid repository owner: $repositoryOwner" }
        require(REPOSITORY_PART.matches(repositoryName)) { "invalid repository name: $repositoryName" }
    }

    companion object {
        private val REPOSITORY_PART = Regex("[A-Za-z0-9_.-]+")
    }
}

/** Exact external artifact allowed only under server-side trust policy. */
class ExternalArtifact(
    val uri: URI,
    val size: Long,
    val sha256: String,
) {
    init {
        require(uri.scheme.equals("https", true) && !uri.host.isNullOrBlank()) { "external artifact must use HTTPS" }
        require(size > 0) { "external artifact size must be positive" }
        require(SHA_256.matches(sha256)) { "external artifact SHA-256 is invalid" }
    }

    companion object {
        private val SHA_256 = Regex("[0-9a-fA-F]{64}")
    }
}

/** Required third-party plugin which may be manual-only or have an exact verified artifact. */
class ExternalPluginDependency private constructor(builder: Builder) : PluginDependency {
    override val external: ExternalPluginDependency get() = this
    val plugin: String = builder.plugin
    val minimumVersion: SemanticVersion = builder.minimumVersion
    override val versions: VersionConstraint = builder.versions()
    val downloadPage: URI? = builder.downloadPage
    val artifact: ExternalArtifact? = builder.artifact
    override val required: Boolean = builder.required
    override val downloadPolicy: DownloadPolicy = builder.downloadPolicy

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

        fun downloadPage(url: String) = apply {
            val parsed = URI.create(url)
            require(parsed.scheme.equals("https", true) && !parsed.host.isNullOrBlank()) { "download page must use HTTPS" }
            downloadPage = parsed
        }

        fun artifact(url: String, size: Long, sha256: String) = apply {
            artifact = ExternalArtifact(URI.create(url), size, sha256)
        }
        fun required(value: Boolean) = apply { required = value }
        fun maximumVersion(value: String) = apply { maximumInclusive = SemanticVersion.parse(value) }
        fun maximumVersionExclusive(value: String) = apply { maximumExclusive = SemanticVersion.parse(value) }
        fun downloadPolicy(value: DownloadPolicy) = apply { downloadPolicy = value }
        fun automaticDownload(value: Boolean) = apply {
            downloadPolicy = if (value) DownloadPolicy.AUTOMATIC else DownloadPolicy.MANUAL
        }
        fun forceAutomaticDownload(value: Boolean) = apply {
            if (value) downloadPolicy = DownloadPolicy.FORCED
            else if (downloadPolicy == DownloadPolicy.FORCED) downloadPolicy = DownloadPolicy.MANUAL
        }

        internal fun versions() = VersionConstraint(minimumVersion, maximumInclusive, maximumExclusive)

        fun build(): ExternalPluginDependency {
            require(downloadPage != null || artifact != null) { "external dependency requires a download page or artifact" }
            require(artifact != null || downloadPolicy == DownloadPolicy.MANUAL) {
                "a download page cannot be installed automatically"
            }
            versions()
            return ExternalPluginDependency(this)
        }
    }

    companion object {
        @JvmStatic fun builder(plugin: String, minimumVersion: String): Builder {
            require(plugin.isNotBlank()) { "external plugin name must not be blank" }
            return Builder(plugin.trim(), SemanticVersion.parse(minimumVersion))
        }
    }
}

/** One exact platform/Java release artifact. */
class ArtifactDescriptor(
    val file: String,
    val platform: PlatformType,
    val minimumJava: Int,
    val maximumJava: Int?,
    val size: Long,
    val sha256: String,
    val downloadUri: URI?,
) {
    init {
        require(SAFE_FILE.matches(file) && file.endsWith(".jar", true)) { "unsafe artifact filename: $file" }
        require(minimumJava >= 8) { "minimum Java must be at least 8" }
        require(maximumJava == null || maximumJava >= minimumJava) { "maximum Java must be >= minimum Java" }
        require(size > 0) { "artifact size must be positive" }
        require(SHA_256.matches(sha256)) { "artifact SHA-256 is invalid" }
        require(downloadUri == null || downloadUri.scheme.equals("https", true)) { "artifact URL must use HTTPS" }
    }

    fun supports(javaFeature: Int): Boolean = javaFeature >= minimumJava &&
        (maximumJava == null || javaFeature <= maximumJava)

    companion object {
        private val SAFE_FILE = Regex("[A-Za-z0-9][A-Za-z0-9._-]*")
        private val SHA_256 = Regex("[0-9a-fA-F]{64}")
    }
}

/** Installed component metadata shared by embedded descriptors and explicit registration. */
class ProductDescriptor private constructor(builder: Builder) {
    val id: ProductId = builder.id
    val version: SemanticVersion = builder.version
    val supportedApi: ApiVersionRange = builder.supportedApi
        ?: throw IllegalArgumentException("pnLibrary API range is required")
    val managedProductDependencies: List<ManagedProductDependency> = Collections.unmodifiableList(builder.managedProductDependencies.toList())
    val externalPluginDependencies: List<ExternalPluginDependency> = Collections.unmodifiableList(builder.externalPluginDependencies.toList())

    class Builder internal constructor(
        internal val id: ProductId,
        internal val version: SemanticVersion,
    ) {
        internal var supportedApi: ApiVersionRange? = null
        internal val managedProductDependencies = mutableListOf<ManagedProductDependency>()
        internal val externalPluginDependencies = mutableListOf<ExternalPluginDependency>()

        fun pnLibraryApi(minimum: Int, maximum: Int) = apply {
            supportedApi = ApiVersionRange(minimum, maximum)
        }

        @JvmOverloads
        fun managedDependency(
            component: String,
            minimumVersion: String,
            repositoryOwner: String,
            repositoryName: String,
            required: Boolean = true,
            automaticDownload: Boolean = false,
        ) = apply {
            val dependency = ManagedProductDependency(
                ProductId.of(component), SemanticVersion.parse(minimumVersion), repositoryOwner, repositoryName,
                required, if (automaticDownload) DownloadPolicy.AUTOMATIC else DownloadPolicy.MANUAL,
            )
            require(managedProductDependencies.none { it.product == dependency.product }) {
                "duplicate component dependency: ${dependency.product}"
            }
            managedProductDependencies += dependency
        }

        fun externalDependency(dependency: ExternalPluginDependency) = apply {
            require(externalPluginDependencies.none { it.plugin.equals(dependency.plugin, true) }) {
                "duplicate external dependency: ${dependency.plugin}"
            }
            externalPluginDependencies += dependency
        }

        fun build(): ProductDescriptor = ProductDescriptor(this)
    }

    companion object {
        @JvmStatic fun builder(id: String, version: String): Builder =
            Builder(ProductId.of(id), SemanticVersion.parse(version))

        /** Descriptor for pnLibrary itself when no generated descriptor is available. */
        @JvmStatic fun library(version: String): ProductDescriptor = builder("pnlibrary", version)
            .pnLibraryApi(PnLibraryApi.VERSION, PnLibraryApi.VERSION).build()
    }
}

/** Immutable observable graph-plan state. */
class UpdatePlanSnapshot(
    val id: UUID,
    val revision: Long,
    val state: UpdateState,
    val plan: UpdatePlan?,
    blockers: List<BlockedReason>,
    val message: String?,
) {
    val blockers: List<BlockedReason> = Collections.unmodifiableList(blockers.toList())

    init {
        require(revision >= 0) { "plan revision must not be negative" }
    }
}
