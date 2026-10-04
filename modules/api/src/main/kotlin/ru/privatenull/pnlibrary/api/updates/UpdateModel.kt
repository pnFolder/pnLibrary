package ru.privatenull.pnlibrary.api.updates

import ru.privatenull.pnlibrary.api.version.ApiVersionRange
import ru.privatenull.pnlibrary.api.version.PnLibraryApi
import ru.privatenull.pnlibrary.api.version.SemanticVersion
import ru.privatenull.pnlibrary.api.platform.PlatformType
import java.net.URI
import java.time.Instant
import java.util.Collections
import java.util.UUID
import java.util.Locale
import ru.privatenull.pnlibrary.api.plugin.PluginDependency
import ru.privatenull.pnlibrary.api.plugin.DownloadPolicy
import ru.privatenull.pnlibrary.api.plugin.VersionConstraint

/**
 * Stable normalized identity of a pnLibrary-managed product.
 *
 * @property value lowercase validated product identifier
 */
class ProductId private constructor(val value: String) : Comparable<ProductId> {

    /** Compares product identifiers lexicographically. */
    override fun compareTo(other: ProductId): Int = value.compareTo(other.value)

    /** Returns whether [other] represents the same normalized identifier. */
    override fun equals(other: Any?): Boolean = other is ProductId && value == other.value

    /** Returns the normalized identifier hash. */
    override fun hashCode(): Int = value.hashCode()

    /** Returns [value] without additional formatting. */
    override fun toString(): String = value

    /** Creates validated product identifiers. */
    companion object {
        private val VALID = Regex("[a-z0-9][a-z0-9_.-]*")

        /** Normalizes and validates [value]. */
        @JvmStatic
        fun of(value: String): ProductId {
            val normalized = value.trim().lowercase(Locale.ROOT)
            require(VALID.matches(normalized)) { "Invalid product ID: $value" }
            return ProductId(normalized)
        }
    }
}

/**
 * Minimum semantic version required from another managed component.
 *
 * @property product required managed product
 * @property minimumVersion oldest acceptable product version
 */
data class ProductDependency(
    val product: ProductId,
    val minimumVersion: SemanticVersion,
)

/**
 * Installed component state used as the starting point for resolution.
 *
 * @property product installed product identity
 * @property version currently running version
 * @property supportedApi pnLibrary API generations accepted by this installation
 * @property providesApi API generation supplied by this product, when applicable
 */
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

/**
 * Normalized release metadata available from a release source.
 *
 * @property product released product identity
 * @property version released semantic version
 * @property channel declared release maturity
 * @property supportedApi pnLibrary API generations supported by this release
 * @property providesApi API generation supplied by this product, when applicable
 * @property dependencies managed product requirements
 * @property repository source repository in `owner/name` form
 * @property artifacts downloadable platform artifacts
 * @property externalPluginDependencies third-party plugin requirements
 * @property publishedAt publisher-provided release time
 */
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
    val publishedAt: Instant? = null,
) {
    init {
        require(providesApi == null || providesApi > 0) { "provided API generation must be positive" }
        require(dependencies.map(ProductDependency::product).distinct().size == dependencies.size) {
            "product release contains duplicate dependencies"
        }
    }
}

/**
 * One version transition selected by the resolver.
 *
 * @property product product being installed or upgraded
 * @property from currently installed version, or `null` for a new product
 * @property to selected target version
 */
data class ProductChange(
    val product: ProductId,
    val from: SemanticVersion?,
    val to: SemanticVersion,
)

/**
 * Immutable atomic update target.
 *
 * @property targetApi pnLibrary API generation selected for the complete graph
 * @property changes version transitions required to apply the plan
 * @property selected complete release set chosen by the resolver
 */
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
    /**
     * Product cannot run against the API generation required by the graph.
     *
     * @property product incompatible product
     * @property supportedApi API generations accepted by the product
     * @property requiredApi API generation required by the candidate graph
     * @property repository source repository, when known
     */
    data class ApiMismatch(
        val product: ProductId,
        val supportedApi: ApiVersionRange,
        val requiredApi: Int,
        val repository: String? = null,
    ) : BlockedReason()

    /**
     * Required managed product version is unavailable.
     *
     * @property product product declaring the dependency
     * @property dependency missing managed product
     * @property minimumVersion oldest acceptable dependency version
     */
    data class MissingDependency(
        val product: ProductId,
        val dependency: ProductId,
        val minimumVersion: SemanticVersion,
    ) : BlockedReason()

    /**
     * Required third-party plugin is absent or too old.
     *
     * @property product product declaring the dependency
     * @property plugin third-party plugin name
     * @property minimumVersion oldest acceptable plugin version
     * @property downloadPage optional administrator-facing download page
     */
    data class MissingExternalPluginDependency(
        val product: ProductId,
        val plugin: String,
        val minimumVersion: SemanticVersion,
        val downloadPage: String?,
    ) : BlockedReason()

    /**
     * Product updates are explicitly frozen.
     *
     * @property product frozen managed product
     */
    data class Frozen(val product: ProductId) : BlockedReason()

    /**
     * No published release satisfies the required API generation.
     *
     * @property product product without a compatible release
     * @property requiredApi API generation required by the candidate graph
     */
    data class NoCompatibleRelease(val product: ProductId, val requiredApi: Int) : BlockedReason()
}

/**
 * A managed component dependency discoverable through a release catalogue.
 *
 * @property product required managed product
 * @property versions accepted semantic-version interval
 * @property repositoryOwner catalogue repository owner
 * @property repositoryName catalogue repository name
 * @property required whether absence blocks the declaring product
 * @property downloadPolicy administrator/automatic download policy
 */
class ManagedProductDependency(
    val product: ProductId,
    override val versions: VersionConstraint,
    val repositoryOwner: String,
    val repositoryName: String,
    override val required: Boolean = true,
    override val downloadPolicy: DownloadPolicy = DownloadPolicy.MANUAL,
) : PluginDependency {
    /** Oldest accepted managed product version. */
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
    /** Returns this dependency as its managed representation. */
    override val managed: ManagedProductDependency get() = this
    init {
        require(REPOSITORY_PART.matches(repositoryOwner)) { "invalid repository owner: $repositoryOwner" }
        require(REPOSITORY_PART.matches(repositoryName)) { "invalid repository name: $repositoryName" }
    }

    /** Validation constants for repository coordinates. */
    companion object {
        private val REPOSITORY_PART = Regex("[A-Za-z0-9_.-]+")
    }
}

/**
 * Exact external artifact allowed only under server-side trust policy.
 *
 * @property uri verified HTTPS artifact location
 * @property size expected byte length, when known
 * @property sha256 expected SHA-256 digest, when known
 */
class ExternalArtifact(
    val uri: URI,
    val size: Long?,
    val sha256: String?,
) {
    init {
        require(uri.scheme.equals("https", true) && !uri.host.isNullOrBlank()) { "external artifact must use HTTPS" }
        require(size == null || size > 0) { "external artifact size must be positive" }
        require(sha256 == null || SHA_256.matches(sha256)) { "external artifact SHA-256 is invalid" }
    }

    /** Integrity validation constants. */
    companion object {
        private val SHA_256 = Regex("[0-9a-fA-F]{64}")
    }
}

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

/**
 * One exact platform/Java release artifact.
 *
 * @property file safe JAR filename
 * @property platform required server/proxy platform
 * @property minimumJava oldest supported Java feature version
 * @property maximumJava newest supported Java feature version, or `null` when unbounded
 * @property size optional publisher-provided byte length
 * @property sha256 optional publisher-provided SHA-256 digest
 * @property downloadUri optional HTTPS artifact location
 */
class ArtifactDescriptor(
    val file: String,
    val platform: PlatformType,
    val minimumJava: Int,
    val maximumJava: Int?,
    /** Optional publisher metadata. The updater computes these values after download. */
    val size: Long? = null,
    val sha256: String? = null,
    val downloadUri: URI?,
) {
    init {
        require(SAFE_FILE.matches(file) && file.endsWith(".jar", true)) { "unsafe artifact filename: $file" }
        require(minimumJava >= 8) { "minimum Java must be at least 8" }
        require(maximumJava == null || maximumJava >= minimumJava) { "maximum Java must be >= minimum Java" }
        require(size == null || size > 0) { "artifact size must be positive" }
        require(sha256 == null || SHA_256.matches(sha256)) { "artifact SHA-256 is invalid" }
        require(downloadUri == null || downloadUri.scheme.equals("https", true)) { "artifact URL must use HTTPS" }
    }

    /** Returns whether [javaFeature] is inside this artifact's Java range. */
    fun supports(javaFeature: Int): Boolean = javaFeature >= minimumJava &&
        (maximumJava == null || javaFeature <= maximumJava)

    /** Filename and integrity validation constants. */
    companion object {
        private val SAFE_FILE = Regex("[A-Za-z0-9][A-Za-z0-9._-]*")
        private val SHA_256 = Regex("[0-9a-fA-F]{64}")
    }
}

/** Installed component metadata shared by embedded descriptors and explicit registration. */
class ProductDescriptor private constructor(
    builder: Builder,
    private val boundId: ProductId? = null,
) {
    /** Product identity assigned during module registration. */
    val id: ProductId get() = requireNotNull(boundId) {
        "Product descriptor must be bound by registerModule before its id is read"
    }
    /** Whether this descriptor has been bound to a product identity. */
    val isBound: Boolean get() = boundId != null
    /** Installed semantic version. */
    val version: SemanticVersion = builder.version
        ?: throw IllegalArgumentException("product version is required")
    /** Inclusive pnLibrary API generations accepted by this product. */
    val supportedApi: ApiVersionRange = builder.supportedApi
        ?: throw IllegalArgumentException("pnLibrary API range is required")
    /** Maximum-risk release channel accepted by this product. */
    val channel: UpdateChannel = builder.channel
    /** Oldest Java feature version supported by this installation. */
    val minimumJava: Int = builder.minimumJava
    /** Newest Java feature version supported, or `null` when unbounded. */
    val maximumJava: Int? = builder.maximumJava

    /** Copies this descriptor and binds it to normalized product [id]. */
    fun bindTo(id: String): ProductDescriptor = ProductDescriptor(
        Builder()
            .version(version.toString())
            .pnLibraryApi(supportedApi.minimum, supportedApi.maximum)
            .channel(channel)
            .java(minimumJava, maximumJava),
        ProductId.of(id),
    )

    /** Fluent builder for installed product metadata. */
    class Builder internal constructor() {
        internal var version: SemanticVersion? = null
        internal var supportedApi: ApiVersionRange? = null
        internal var channel: UpdateChannel = UpdateChannel.STABLE
        internal var minimumJava: Int = 8
        internal var maximumJava: Int? = null

        /** Sets the inclusive pnLibrary API-generation range. */
        fun pnLibraryApi(minimum: Int, maximum: Int) = apply {
            supportedApi = ApiVersionRange(minimum, maximum)
        }

        /** Parses and sets the installed semantic [value]. */
        fun version(value: String) = apply { version = SemanticVersion.parse(value) }

        /** Sets the maximum-risk update channel. */
        fun channel(value: UpdateChannel) = apply { channel = value }

        /** Sets the inclusive Java feature-version range. */
        @JvmOverloads
        fun java(minimum: Int, maximum: Int? = null) = apply {
            require(minimum >= 8) { "minimum Java must be at least 8" }
            require(maximum == null || maximum >= minimum) { "maximum Java must be >= minimum Java" }
            minimumJava = minimum
            maximumJava = maximum
        }

        /** Validates required metadata and creates an unbound descriptor. */
        fun build(): ProductDescriptor = ProductDescriptor(this)
    }

    /** Creates product descriptors and standard library metadata. */
    companion object {
        /** Returns an empty product-descriptor builder. */
        @JvmStatic
        fun builder(): Builder =
            Builder()

        /** Descriptor for pnLibrary itself when no generated descriptor is available. */
        @JvmStatic
        fun library(version: String): ProductDescriptor = builder()
            .version(version)
            .pnLibraryApi(PnLibraryApi.VERSION, PnLibraryApi.VERSION)
            .build()
            .bindTo("pnlibrary")
    }
}

/**
 * Immutable observable graph-plan state.
 *
 * @property id stable plan identity used for stage and confirmation operations
 * @property revision monotonic state revision
 * @property state current graph-update phase
 * @property plan selected atomic update plan, when resolution succeeded
 * @property blockers immutable reasons preventing a complete compatible plan
 * @property message optional diagnostic or user-facing state detail
 */
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
