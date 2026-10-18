package ru.privatenull.pnlibrary.api.updates

import java.util.Collections
import java.util.UUID

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
    plan: UpdatePlan?,
    blockers: List<BlockedReason>,
    val message: String?,
) {
    /** Detached graph with immutable lists, safe to share with observers. */
    val plan: UpdatePlan? = plan?.copy(
        changes = immutableCopy(plan.changes),
        selected = immutableCopy(plan.selected.map { release ->
            release.copy(
                dependencies = immutableCopy(release.dependencies),
                artifacts = immutableCopy(release.artifacts),
                externalPluginDependencies = immutableCopy(release.externalPluginDependencies),
            )
        }),
    )
    val blockers: List<BlockedReason> = Collections.unmodifiableList(blockers.toList())

    init {
        require(revision >= 0) { "Plan revision must not be negative" }
    }

    private fun <T> immutableCopy(values: List<T>): List<T> = Collections.unmodifiableList(ArrayList(values))
}
