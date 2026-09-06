package ru.privatenull.pnlibrary.api.updates

enum class UpdateChannel { STABLE, BETA, ALPHA }
enum class UpdateState { CHECKING, CURRENT, AVAILABLE, DOWNLOADED, FAILED }

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

class PluginUpdateArtifact(
    val pattern: String,
    val minimumJava: Int,
    val maximumJava: Int?,
) {
    fun supports(javaFeature: Int): Boolean =
        javaFeature >= minimumJava && (maximumJava == null || javaFeature <= maximumJava)
}

class PluginUpdateRequest private constructor(builder: Builder) {
    val repositoryOwner: String = builder.repositoryOwner
    val repositoryName: String = builder.repositoryName
    val channel: UpdateChannel = builder.channel
    val automaticDownload: Boolean = builder.automaticDownload
    val artifacts: List<PluginUpdateArtifact> = builder.artifacts.toList()

    fun artifactFor(javaFeature: Int): PluginUpdateArtifact? = artifacts
        .filter { it.supports(javaFeature) }
        .maxByOrNull { it.minimumJava }

    class Builder internal constructor() {
        internal var repositoryOwner = ""
        internal var repositoryName = ""
        internal var channel = UpdateChannel.STABLE
        internal var automaticDownload = true
        internal val artifacts = mutableListOf<PluginUpdateArtifact>()

        fun repository(owner: String, name: String) = apply {
            require(owner.matches(Regex("[A-Za-z0-9_.-]+")) && name.matches(Regex("[A-Za-z0-9_.-]+")))
            repositoryOwner = owner; repositoryName = name
        }
        fun channel(value: UpdateChannel) = apply { channel = value }
        fun automaticDownload(enabled: Boolean) = apply { automaticDownload = enabled }
        fun artifactPattern(regex: String) = artifact(regex, 8)
        @JvmOverloads
        fun artifact(regex: String, minimumJava: Int, maximumJava: Int? = null) = apply {
            Regex(regex)
            require(minimumJava >= 8) { "minimumJava must be at least 8" }
            require(maximumJava == null || maximumJava >= minimumJava) { "maximumJava must be >= minimumJava" }
            artifacts += PluginUpdateArtifact(regex, minimumJava, maximumJava)
        }
        fun build(): PluginUpdateRequest {
            require(repositoryOwner.isNotBlank() && repositoryName.isNotBlank()) { "repository is required" }
            require(artifacts.isNotEmpty()) { "at least one artifact is required" }
            return PluginUpdateRequest(this)
        }
    }

    companion object { @JvmStatic fun builder(): Builder = Builder() }
}

interface UpdateRegistration : AutoCloseable {
    val repository: String
    val snapshot: UpdateSnapshot
    fun checkNow()
    fun downloadNow()
    override fun close()
}

interface UpdateService {
    fun register(owner: Any, request: PluginUpdateRequest): UpdateRegistration
    fun registrations(): List<UpdateRegistration>
    fun find(product: String): UpdateRegistration?
}
