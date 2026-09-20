package ru.privatenull.pnlibrary.api.updates

import ru.privatenull.pnlibrary.api.version.ApiVersionRange
import ru.privatenull.pnlibrary.api.version.PnLibraryApi
import ru.privatenull.pnlibrary.api.version.SemanticVersion
import ru.privatenull.pnlibrary.api.platform.PlatformType
import java.net.URI
import java.util.Collections
import java.util.UUID

/** Stable normalized identity of a pnLibrary-managed component. */
class ComponentId private constructor(val value: String) : Comparable<ComponentId> {
    override fun compareTo(other: ComponentId): Int = value.compareTo(other.value)
    override fun equals(other: Any?): Boolean = other is ComponentId && value == other.value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = value

    companion object {
        private val VALID = Regex("[a-z0-9][a-z0-9_.-]*")

        @JvmStatic
        fun of(value: String): ComponentId {
            val normalized = value.trim().lowercase()
            require(VALID.matches(normalized)) { "Invalid component ID: $value" }
            return ComponentId(normalized)
        }
    }
}

/** Minimum semantic version required from another managed component. */
data class ComponentDependency(
    val component: ComponentId,
    val minimumVersion: SemanticVersion,
)

/** Installed component state used as the starting point for resolution. */
data class InstalledComponent(
    val component: ComponentId,
    val version: SemanticVersion,
    val supportedApi: ApiVersionRange,
    val providesApi: Int? = null,
) {
    init {
        require(providesApi == null || providesApi > 0) { "provided API generation must be positive" }
    }
}

/** Normalized release metadata available from a release source. */
data class ComponentRelease(
    val component: ComponentId,
    val version: SemanticVersion,
    val channel: UpdateChannel,
    val supportedApi: ApiVersionRange,
    val providesApi: Int? = null,
    val dependencies: List<ComponentDependency> = emptyList(),
    val repository: String? = null,
    val artifacts: List<ArtifactDescriptor> = emptyList(),
    val externalDependencies: List<ExternalDependency> = emptyList(),
) {
    init {
        require(providesApi == null || providesApi > 0) { "provided API generation must be positive" }
        require(dependencies.map(ComponentDependency::component).distinct().size == dependencies.size) {
            "component release contains duplicate dependencies"
        }
    }
}

/** One version transition selected by the resolver. */
data class ComponentChange(
    val component: ComponentId,
    val from: SemanticVersion?,
    val to: SemanticVersion,
)

/** Immutable atomic update target. */
data class UpdatePlan(
    val targetApi: Int,
    val changes: List<ComponentChange>,
    val selected: List<ComponentRelease>,
) {
    init {
        require(targetApi > 0) { "target API generation must be positive" }
    }
}

/** Structured explanation for a candidate or complete plan that cannot be installed. */
sealed class BlockedReason {
    data class ApiMismatch(
        val component: ComponentId,
        val supportedApi: ApiVersionRange,
        val requiredApi: Int,
        val repository: String? = null,
    ) : BlockedReason()

    data class MissingDependency(
        val component: ComponentId,
        val dependency: ComponentId,
        val minimumVersion: SemanticVersion,
    ) : BlockedReason()

    data class MissingExternalDependency(
        val component: ComponentId,
        val plugin: String,
        val minimumVersion: SemanticVersion,
        val downloadPage: String?,
    ) : BlockedReason()

    data class Frozen(val component: ComponentId) : BlockedReason()
    data class NoCompatibleRelease(val component: ComponentId, val requiredApi: Int) : BlockedReason()
}

/** A managed component dependency discoverable through a release catalogue. */
class ManagedDependency(
    val component: ComponentId,
    val minimumVersion: SemanticVersion,
    val repositoryOwner: String,
    val repositoryName: String,
) {
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
class ExternalDependency private constructor(builder: Builder) {
    val plugin: String = builder.plugin
    val minimumVersion: SemanticVersion = builder.minimumVersion
    val downloadPage: URI? = builder.downloadPage
    val artifact: ExternalArtifact? = builder.artifact

    class Builder internal constructor(
        internal val plugin: String,
        internal val minimumVersion: SemanticVersion,
    ) {
        internal var downloadPage: URI? = null
        internal var artifact: ExternalArtifact? = null

        fun downloadPage(url: String) = apply {
            val parsed = URI.create(url)
            require(parsed.scheme.equals("https", true) && !parsed.host.isNullOrBlank()) { "download page must use HTTPS" }
            downloadPage = parsed
        }

        fun artifact(url: String, size: Long, sha256: String) = apply {
            artifact = ExternalArtifact(URI.create(url), size, sha256)
        }

        fun build(): ExternalDependency {
            require(downloadPage != null || artifact != null) { "external dependency requires a download page or artifact" }
            return ExternalDependency(this)
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
class ComponentDescriptor private constructor(builder: Builder) {
    val id: ComponentId = builder.id
    val version: SemanticVersion = builder.version
    val supportedApi: ApiVersionRange = builder.supportedApi
        ?: throw IllegalArgumentException("pnLibrary API range is required")
    val managedDependencies: List<ManagedDependency> = Collections.unmodifiableList(builder.managedDependencies.toList())
    val externalDependencies: List<ExternalDependency> = Collections.unmodifiableList(builder.externalDependencies.toList())

    class Builder internal constructor(
        internal val id: ComponentId,
        internal val version: SemanticVersion,
    ) {
        internal var supportedApi: ApiVersionRange? = null
        internal val managedDependencies = mutableListOf<ManagedDependency>()
        internal val externalDependencies = mutableListOf<ExternalDependency>()

        fun pnLibraryApi(minimum: Int, maximum: Int) = apply {
            supportedApi = ApiVersionRange(minimum, maximum)
        }

        fun managedDependency(
            component: String,
            minimumVersion: String,
            repositoryOwner: String,
            repositoryName: String,
        ) = apply {
            val dependency = ManagedDependency(
                ComponentId.of(component), SemanticVersion.parse(minimumVersion), repositoryOwner, repositoryName,
            )
            require(managedDependencies.none { it.component == dependency.component }) {
                "duplicate component dependency: ${dependency.component}"
            }
            managedDependencies += dependency
        }

        fun externalDependency(dependency: ExternalDependency) = apply {
            require(externalDependencies.none { it.plugin.equals(dependency.plugin, true) }) {
                "duplicate external dependency: ${dependency.plugin}"
            }
            externalDependencies += dependency
        }

        fun build(): ComponentDescriptor = ComponentDescriptor(this)
    }

    companion object {
        @JvmStatic fun builder(id: String, version: String): Builder =
            Builder(ComponentId.of(id), SemanticVersion.parse(version))

        /** Descriptor for pnLibrary itself when no generated descriptor is available. */
        @JvmStatic fun library(version: String): ComponentDescriptor = builder("pnlibrary", version)
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
