package ru.privatenull.pnlibrary.api.updates

import ru.privatenull.pnlibrary.api.version.ApiVersionRange
import ru.privatenull.pnlibrary.api.version.PnLibraryApi
import ru.privatenull.pnlibrary.api.version.SemanticVersion
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.api.plugin.PluginDependency
import java.util.Optional
import java.util.UUID
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/** Release maturity accepted by an updater. Each channel includes more stable releases. */
enum class UpdateChannel {
    /** Stable releases only; prereleases, alpha, beta, and release candidates are excluded. */
    STABLE,
    /** Stable and beta releases; alpha releases remain excluded. */
    BETA,
    /** Every release, including experimental alpha versions. */
    ALPHA,
    /** Every published build, including development releases. */
    DEV;

    /** Returns whether this maximum-risk channel permits [candidate]. */
    fun accepts(candidate: UpdateChannel): Boolean = candidate.ordinal <= ordinal
}

/** Current observable phase of an update registration. */
enum class UpdateState {
    /** The installed component is the newest allowed compatible version. */
    UP_TO_DATE,
    /** A newer allowed compatible component version exists. */
    UPDATE_AVAILABLE,
    /** A verified update transaction is staged for restart. */
    UPDATE_STAGED,
    /** Updates for this component are temporarily frozen. */
    FROZEN,
    /** A release exists but cannot run with the selected API or dependencies. */
    INCOMPATIBLE,
    /** A related component prevents a valid atomic update plan. */
    BLOCKED,
    /** A remote release check is currently running. */
    CHECKING,
    /** A complete plan is currently being downloaded and verified. */
    DOWNLOADING,
    /** No newer compatible release was found. */
    CURRENT,
    /** A newer compatible release exists but has not been downloaded. */
    AVAILABLE,
    /** A verified update artifact was placed in the platform update directory. */
    DOWNLOADED,
    /** The latest check or download failed; details are available in the snapshot message. */
    FAILED,
}

/**
 * Immutable point-in-time state of one registered product updater.
 *
 * @property product display name resolved from native plugin metadata
 * @property currentVersion version currently running
 * @property latestVersion newest compatible release version, when known
 * @property channel configured release maturity
 * @property state current updater phase
 * @property currentJava Java feature version running the server
 * @property requiredJava minimum Java version of the selected artifact
 * @property automaticDownload whether checks may download a verified artifact automatically
 * @property releaseUrl public release page, when one is known
 * @property message failure or status detail intended for diagnostics, when present
 */
class UpdateSnapshot(
    val product: String,
    val currentVersion: String,
    val latestVersion: String?,
    val channel: UpdateChannel,
    val state: UpdateState,
    val currentJava: Int,
    val requiredJava: Int,
    val automaticDownload: Boolean,
    val releaseUrl: String?,
    val message: String?,
)

/**
 * Release-asset name pattern bounded to a range of Java feature versions.
 *
 * @property pattern regular expression matched against complete release asset names
 * @property minimumJava lowest supported Java feature version, inclusive
 * @property maximumJava highest supported Java feature version, inclusive, or `null`
 */
class PluginUpdateArtifact(
    val pattern: String,
    val minimumJava: Int,
    val maximumJava: Int?,
    val platform: PlatformType? = null,
) {
    /** Returns whether [javaFeature] is inside this artifact's inclusive Java range. */
    fun supports(javaFeature: Int): Boolean =
        javaFeature >= minimumJava && (maximumJava == null || javaFeature <= maximumJava)
}

/**
 * Validated GitHub release and artifact-selection policy for one plugin.
 *
 * Use [builder] to configure the repository, channel, and at least one artifact. When
 * ranges overlap, [artifactFor] chooses the compatible artifact with the highest
 * [PluginUpdateArtifact.minimumJava], allowing newer runtimes to receive newer builds.
 *
 * ```kotlin
 * val request = PluginUpdateRequest.builder()
 *     .repository("example", "ExamplePlugin")
 *     .channel(UpdateChannel.STABLE)
 *     .artifact("(?i)^example-java8-.*\\.jar$", 8, 16)
 *     .artifact("(?i)^example-java17-.*\\.jar$", 17)
 *     .build()
 * ```
 */
class PluginUpdateRequest private constructor(builder: Builder) {
    /** GitHub repository owner. */
    val repositoryOwner: String = builder.repositoryOwner
    /** GitHub repository name. */
    val repositoryName: String = builder.repositoryName
    /** Highest prerelease maturity accepted by the updater. */
    val channel: UpdateChannel = builder.channel
    /** Whether a newly discovered compatible artifact should be downloaded. */
    val automaticDownload: Boolean = builder.automaticDownload
    /** Immutable artifact-selection rules in declaration order. */
    val artifacts: List<PluginUpdateArtifact> = Collections.unmodifiableList(ArrayList(builder.artifacts))
    /** Inclusive pnLibrary API generations supported by this product. */
    val supportedApi: ApiVersionRange = builder.supportedApi

    /** Returns the most specific artifact compatible with [javaFeature], or `null`. */
    fun artifactFor(javaFeature: Int): PluginUpdateArtifact? = artifacts
        .filter { it.supports(javaFeature) }
        .maxByOrNull { it.minimumJava }

    /** Returns the most specific artifact compatible with both runtime and platform. */
    fun artifactFor(javaFeature: Int, platform: PlatformType): PluginUpdateArtifact? = artifacts
        .filter { it.supports(javaFeature) && (it.platform == null || it.platform == platform) }
        .maxByOrNull { it.minimumJava }

    /** Mutable Java-friendly builder for [PluginUpdateRequest]. */
    class Builder internal constructor() {
        internal var repositoryOwner = ""
        internal var repositoryName = ""
        internal var channel = UpdateChannel.STABLE
        internal var automaticDownload = true
        internal val artifacts = mutableListOf<PluginUpdateArtifact>()
        internal var supportedApi = ApiVersionRange(PnLibraryApi.VERSION, PnLibraryApi.VERSION)

        /** Sets and validates the GitHub repository coordinates. */
        fun repository(owner: String, name: String) = apply {
            require(owner.matches(REPOSITORY_PART) && name.matches(REPOSITORY_PART)) {
                "Invalid GitHub repository: $owner/$name"
            }
            repositoryOwner = owner
            repositoryName = name
        }
        /** Sets the accepted release [value]. */
        fun channel(value: UpdateChannel) = apply { channel = value }
        /** Enables or disables automatic verified downloads. */
        fun automaticDownload(enabled: Boolean) = apply { automaticDownload = enabled }
        /** Sets the inclusive pnLibrary API-generation range supported by the plugin. */
        fun supportedApi(minimum: Int, maximum: Int) = apply {
            supportedApi = ApiVersionRange(minimum, maximum)
        }
        /** Declares one supported API generation. */
        fun apiVersion(version: Int) = supportedApi(version, version)
        /** Declares the inclusive range of supported pnLibrary API generations. */
        fun apiVersions(minimum: Int, maximum: Int) = supportedApi(minimum, maximum)
        /** Adds an exact release asset name without exposing regular-expression escaping. */
        @JvmOverloads
        fun exactArtifact(name: String, minimumJava: Int = 8, maximumJava: Int? = null) = apply {
            require(name.isNotBlank()) { "artifact name must not be blank" }
            artifact("^${Regex.escape(name)}$", minimumJava, maximumJava)
        }
        /** Adds an artifact pattern compatible with Java 8 and newer. */
        fun artifactPattern(regex: String) = artifact(regex, 8)
        /** Adds and validates one Java-bounded release artifact rule. */
        @JvmOverloads
        fun artifact(regex: String, minimumJava: Int, maximumJava: Int? = null) = apply {
            Regex(regex)
            require(minimumJava >= 8) { "minimumJava must be at least 8" }
            require(maximumJava == null || maximumJava >= minimumJava) { "maximumJava must be >= minimumJava" }
            artifacts += PluginUpdateArtifact(regex, minimumJava, maximumJava)
        }
        /** Adds a release artifact restricted to one server platform. */
        @JvmOverloads
        fun artifact(regex: String, platform: PlatformType, minimumJava: Int, maximumJava: Int? = null) = apply {
            Regex(regex)
            require(minimumJava >= 8) { "minimumJava must be at least 8" }
            require(maximumJava == null || maximumJava >= minimumJava) { "maximumJava must be >= minimumJava" }
            artifacts += PluginUpdateArtifact(regex, minimumJava, maximumJava, platform)
        }
        /** Validates the complete policy and creates its immutable request. */
        fun build(): PluginUpdateRequest {
            require(repositoryOwner.isNotBlank() && repositoryName.isNotBlank()) { "repository is required" }
            require(artifacts.isNotEmpty()) { "at least one artifact is required" }
            return PluginUpdateRequest(this)
        }
    }

    /** Java-friendly entry point for constructing validated update requests. */
    companion object {
        private val REPOSITORY_PART = Regex("[A-Za-z0-9_.-]+")

        /** Creates an empty update-request builder. */
        @JvmStatic
        fun builder(): Builder = Builder()
    }
}

/** Lifecycle and manual controls for one updater registration. */
interface UpdateRegistration : AutoCloseable {
    /** Whether this registration has been removed and rejects new manual work. */
    val isClosed: Boolean get() = false
    /** Repository coordinate in `owner/name` form. */
    val repository: String
    /** Latest immutable state observed by this registration. */
    val snapshot: UpdateSnapshot
    /** Starts a background check without forcing a download. */
    fun checkNow()
    /** Starts a background check with downloading enabled for this invocation. */
    fun downloadNow()
    /** Stops periodic and manual work and removes this registration. */
    override fun close()
}

/**
 * Registers and queries plugin update monitors owned by this runtime.
 *
 * Registration and lookup are safe from arbitrary threads. Graph checks are serialized by the
 * runtime and concurrent [checkNow] calls share the active check. Completion stages finish on the
 * update executor; callers must dispatch platform mutations through their platform/task API.
 */
interface UpdateService {
    /** Registers and immediately starts monitoring [product] represented by [owner]. */
    fun register(
        owner: Any,
        product: ProductDescriptor,
        request: PluginUpdateRequest,
        dependencies: List<PluginDependency> = emptyList(),
    ): UpdateRegistration
    /** Returns an immutable snapshot of current registrations. */
    @Suppress("DEPRECATION")
    fun all(): List<UpdateRegistration> = Collections.unmodifiableList(ArrayList(registrations()))

    /** Finds a registration by product or repository name, ignoring case. */
    @Suppress("DEPRECATION")
    fun get(product: String): UpdateRegistration? = find(product)

    /** Returns a registration or fails with the requested product in the message. */
    fun require(product: String): UpdateRegistration =
        get(product) ?: error("Update registration '$product' is unavailable")

    /** Compatibility alias for [all]. Implementations should return a stable snapshot. */
    @Deprecated("Use all()", ReplaceWith("all()"))
    fun registrations(): List<UpdateRegistration>

    /** Compatibility alias for [get]. */
    @Deprecated("Use get(product)", ReplaceWith("get(product)"))
    fun find(product: String): UpdateRegistration?

    /** Refreshes every registered catalogue and resolves one complete compatibility plan. */
    fun checkNow(): CompletionStage<UpdatePlanSnapshot> = unsupported("graph update checks")
    /** Returns the latest immutable graph plan, when one has been resolved. */
    fun currentPlan(): Optional<UpdatePlanSnapshot> = Optional.empty()
    /** Downloads and verifies every artifact in the selected plan. */
    fun stage(planId: UUID): CompletionStage<UpdatePlanSnapshot> = unsupported("graph update staging")
    /** Confirms a token-bound sensitive action for the selected plan. */
    fun confirm(planId: UUID, token: String): CompletionStage<UpdatePlanSnapshot> = unsupported("graph update confirmation")
    /** Returns a bounded, newest-first, immutable graph-plan snapshot. */
    fun history(): List<UpdatePlanSnapshot> = emptyList()

    private fun unsupported(operation: String): CompletionStage<UpdatePlanSnapshot> =
        CompletableFuture<UpdatePlanSnapshot>().also {
            it.completeExceptionally(UnsupportedOperationException("Update service does not support $operation"))
        }
}
