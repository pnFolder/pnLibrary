package org.pnfolder.pnlibrary.core.menu

import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.events.EventDay
import org.pnfolder.pnlibrary.api.events.EventInfo
import org.pnfolder.pnlibrary.api.events.EventTrace
import org.pnfolder.pnlibrary.api.events.ListenerInfo
import org.pnfolder.pnlibrary.api.tasks.TaskSnapshot
import org.pnfolder.pnlibrary.api.updates.UpdateActor
import org.pnfolder.pnlibrary.api.updates.UpdateChannel
import org.pnfolder.pnlibrary.api.updates.UpdateSnapshot
import org.pnfolder.pnlibrary.api.updates.UpdateState
import org.pnfolder.pnlibrary.api.updates.backup.UpdateBackup
import org.pnfolder.pnlibrary.api.updates.backup.UpdateHistoryEntry
import org.pnfolder.pnlibrary.core.journal.JournalDetail
import org.pnfolder.pnlibrary.core.journal.JournalGroup
import org.pnfolder.pnlibrary.core.link.ServerGroups
import org.pnfolder.pnlibrary.core.placeholders.PlaceholderInfo
import org.pnfolder.pnlibrary.core.stats.StatsSnapshot
import org.pnfolder.pnlibrary.core.tasks.TaskControl
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletionStage

data class PlanInfo(val state: UpdateState, val hasChanges: Boolean, val blockers: List<String>)

data class PluginRow(val name: String, val version: String?, val modules: Int)

data class EventAbout(val purpose: String, val usage: String, val owner: OwnerInfo?)

data class ViewerInfo(val name: String, val permissions: Set<String>)

enum class GroupOp {
    CREATE,

    DELETE,

    ADD_SERVER,

    REMOVE_SERVER,

    ADD_PLUGIN,

    REMOVE_PLUGIN,

    NEED,

    /** One server of the group does not need a plugin, whatever the rest do. */
    SKIP,

    RESET,
}

data class GroupEdit(val op: GroupOp, val group: String, val server: String? = null, val plugin: String? = null)

/**
 * Everything the menu reads from a server and everything it asks a server to do.
 *
 * The menu is always built by the server the player stands on. It asks a [MenuData] for the facts: this server's own
 * library for its own menu, or another server's library, reached over the link, for the menu of that server. A proxy
 * therefore draws nothing; it answers these calls.
 *
 * Methods that return a [CompletionStage] finish later; the others answer at once on this server and may take a moment
 * on another one, so the menu calls them away from the server thread.
 */
interface MenuData {
    fun version(): String

    fun snapshots(): List<UpdateSnapshot>

    /** The update state of one plugin, or null when it did not register. */
    fun snapshot(product: String): UpdateSnapshot?

    fun plugins(): List<PluginRow>

    /** The current update plan, or null when there is none. */
    fun plan(): PlanInfo?

    /** Until when updates of [product] are paused, or null. */
    fun heldUntil(product: String): Instant?

    fun historyCount(product: String?): CompletionStage<Int>

    fun historyPage(page: Int, product: String?): CompletionStage<List<UpdateHistoryEntry>>

    fun backups(): CompletionStage<List<UpdateBackup>>

    fun checkAll(actor: UpdateActor): CompletionStage<Boolean>

    /** Asks the catalog of one plugin for a new version; it does not wait for the answer. */
    fun checkOne(product: String, actor: UpdateActor)

    fun setAutomatic(product: String, enabled: Boolean, actor: UpdateActor): CompletionStage<Boolean>

    /** Sets the update channel of [product]; null returns it to the default. */
    fun setChannel(product: String, channel: UpdateChannel?, actor: UpdateActor): CompletionStage<Boolean>

    /** Pauses updates of [product] for [duration]; null lifts the pause. */
    fun setHeld(product: String, duration: Duration?, actor: UpdateActor): CompletionStage<Boolean>

    fun rollback(id: UUID, restoreConfigurations: Boolean, actor: UpdateActor): CompletionStage<Boolean>

    /** The journal groups of the last [hours] hours; [level] is `info`, `warning`, `error` or null. */
    fun journalGroups(level: String?, hours: Int, limit: Int): List<JournalGroup>

    /** The whole journal event behind a group, or null when it is no longer there. */
    fun journalDetail(id: String): JournalDetail?

    fun tasks(): List<TaskSnapshot>

    fun stats(): StatsSnapshot

    fun placeholders(): List<PlaceholderInfo>

    fun runTask(id: String): TaskControl

    fun cancelTask(id: String): TaskControl

    fun events(): List<EventInfo>

    fun listeners(): List<ListenerInfo>

    fun eventHistory(eventType: String?): List<EventDay>

    /** How many days of calls are kept. */
    fun eventHistoryDays(): Int

    fun tracedEvents(): List<String>

    fun traces(): List<EventTrace>

    fun clearTraces()

    fun setTracing(eventType: String, enabled: Boolean)

    /** Writes the call history to a file and returns where, or null. */
    fun exportEventHistory(json: Boolean): String?

    /** The purpose and the owner of an event, or null when it is unknown. */
    fun eventAbout(eventType: String): EventAbout?

    /** The plugin called [name], or null. */
    fun ownerNamed(name: String): OwnerInfo?

    fun groups(): List<ServerGroups.Group>

    /** Applies one change to the groups; returns false when there is no such group or the name is unfit. */
    fun editGroup(edit: GroupEdit): Boolean

    fun command(viewer: ViewerInfo, line: String): List<Component>
}
