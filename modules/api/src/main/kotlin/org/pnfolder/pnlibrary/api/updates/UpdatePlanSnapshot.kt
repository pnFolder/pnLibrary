package org.pnfolder.pnlibrary.api.updates

import org.pnfolder.pnlibrary.api.updates.release.UpdatePlan
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
        changes = plan.changes.frozen(),
        selected = plan.selected.map { release ->
            release.copy(
                dependencies = release.dependencies.frozen(),
                artifacts = release.artifacts.frozen(),
                externalPluginDependencies = release.externalPluginDependencies.frozen(),
            )
        }.frozen(),
    )
    val blockers: List<BlockedReason> = blockers.frozen()

    init {
        require(revision >= 0) { "Plan revision must not be negative" }
    }

}
