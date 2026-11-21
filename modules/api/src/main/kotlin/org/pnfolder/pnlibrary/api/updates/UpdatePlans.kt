package org.pnfolder.pnlibrary.api.updates

import org.pnfolder.pnlibrary.api.updates.state.UpdateActor
import org.pnfolder.pnlibrary.api.updates.state.UpdatePlanSnapshot
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletionStage

/** Looking for releases and turning them into a plan that is downloaded, verified and applied at restart. */
interface UpdatePlans {
    /** Asks every catalogue for releases and resolves one plan that fits every registered product together. */
    fun check(actor: UpdateActor = UpdateActor.UNKNOWN): CompletionStage<UpdatePlanSnapshot>

    /** The newest plan, when one has been resolved. */
    fun current(): Optional<UpdatePlanSnapshot>

    /** The latest plans in memory, newest first, bounded. */
    fun recent(): List<UpdatePlanSnapshot>

    /** Downloads and verifies every file of the plan [planId], so it can be applied at the next restart. */
    fun stage(planId: UUID, actor: UpdateActor = UpdateActor.UNKNOWN): CompletionStage<UpdatePlanSnapshot>
}
