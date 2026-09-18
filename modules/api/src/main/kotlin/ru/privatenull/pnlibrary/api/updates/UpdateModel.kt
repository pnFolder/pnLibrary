package ru.privatenull.pnlibrary.api.updates

import ru.privatenull.pnlibrary.api.version.ApiVersionRange
import ru.privatenull.pnlibrary.api.version.SemanticVersion

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
    val from: SemanticVersion,
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

    data class Frozen(val component: ComponentId) : BlockedReason()
    data class NoCompatibleRelease(val component: ComponentId, val requiredApi: Int) : BlockedReason()
}
