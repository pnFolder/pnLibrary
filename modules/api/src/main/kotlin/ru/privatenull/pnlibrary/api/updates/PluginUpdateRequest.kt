package ru.privatenull.pnlibrary.api.updates

import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.api.version.ApiVersionRange
import ru.privatenull.pnlibrary.api.version.PnLibraryApi
import java.nio.file.Path
import java.util.Collections

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

    /** Explicit opt-in configuration backup; absent by default. */
    val configurationBackup: UpdateConfigurationBackup? = builder.configurationBackup

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
        internal var configurationBackup: UpdateConfigurationBackup? = null

        /** Preserves only [files] relative to the component's own [dataDirectory] before updating. */
        fun configurationBackup(dataDirectory: Path, vararg files: String) = apply {
            configurationBackup = UpdateConfigurationBackup(dataDirectory, files.toList())
        }

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
            require(name.isNotBlank()) { "Artifact name must not be blank" }
            artifact("^${Regex.escape(name)}$", minimumJava, maximumJava)
        }

        /** Adds an artifact pattern compatible with Java 8 and newer. */
        fun artifactPattern(regex: String) = artifact(regex, 8)

        /** Adds and validates one Java-bounded release artifact rule. */
        @JvmOverloads
        fun artifact(regex: String, minimumJava: Int, maximumJava: Int? = null) = apply {
            Regex(regex)
            require(minimumJava >= 8) { "Minimum Java must be at least 8" }
            require(maximumJava == null || maximumJava >= minimumJava) { "Maximum Java must be >= minimum Java" }
            artifacts += PluginUpdateArtifact(regex, minimumJava, maximumJava)
        }

        /** Adds a release artifact restricted to one server platform. */
        @JvmOverloads
        fun artifact(regex: String, platform: PlatformType, minimumJava: Int, maximumJava: Int? = null) = apply {
            Regex(regex)
            require(minimumJava >= 8) { "Minimum Java must be at least 8" }
            require(maximumJava == null || maximumJava >= minimumJava) { "Maximum Java must be >= minimum Java" }
            artifacts += PluginUpdateArtifact(regex, minimumJava, maximumJava, platform)
        }

        /** Validates the complete policy and creates its immutable request. */
        fun build(): PluginUpdateRequest {
            require(repositoryOwner.isNotBlank() && repositoryName.isNotBlank()) { "Repository is required" }
            require(artifacts.isNotEmpty()) { "At least one artifact is required" }
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
