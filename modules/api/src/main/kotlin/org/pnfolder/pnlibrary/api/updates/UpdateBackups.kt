package org.pnfolder.pnlibrary.api.updates

import org.pnfolder.pnlibrary.api.updates.backup.UpdateBackup
import org.pnfolder.pnlibrary.api.updates.state.UpdateActor
import org.pnfolder.pnlibrary.api.updates.state.UpdatePlanSnapshot
import java.util.UUID
import java.util.concurrent.CompletionStage

/** The rollback sets kept before each update, and going back to them. */
interface UpdateBackups {
    /** The retained complete sets, newest first when their dates are known. */
    fun list(): CompletionStage<List<UpdateBackup>>

    /** Prepares the newest set that can be rolled back for the next restart. */
    fun rollback(actor: UpdateActor = UpdateActor.UNKNOWN): CompletionStage<UpdatePlanSnapshot>

    /**
     * Prepares exactly the set [transactionId] for the next restart and never substitutes another. With
     * [restoreConfigurations] the saved settings are restored as well, which is an explicit choice.
     */
    fun rollback(
        transactionId: UUID,
        actor: UpdateActor = UpdateActor.UNKNOWN,
        restoreConfigurations: Boolean = false,
    ): CompletionStage<UpdatePlanSnapshot>

    /** Permanently deletes the set [transactionId] with its saved files; a set a restart still verifies is refused. */
    fun delete(transactionId: UUID, actor: UpdateActor = UpdateActor.UNKNOWN): CompletionStage<Void?>

    /**
     * Puts the database [name] of [product] back as it was in the set [transactionId]: the tables in the copy replace the
     * current ones and rows written since are lost, but the state it replaces is kept first. Runs at once.
     */
    fun restoreDatabase(transactionId: UUID, product: String, name: String, actor: UpdateActor = UpdateActor.UNKNOWN): CompletionStage<Void?>

    /**
     * Copies the saved databases and exports of [product] from the set [transactionId] into a new folder under
     * `plugins/pnLibrary/data/updates/handout`, for a plugin that cannot take them back itself, and returns its path.
     */
    fun extract(transactionId: UUID, product: String, actor: UpdateActor = UpdateActor.UNKNOWN): CompletionStage<String>
}
