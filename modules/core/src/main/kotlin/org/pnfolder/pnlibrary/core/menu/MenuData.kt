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
import org.pnfolder.pnlibrary.core.lifecycle.JournalDetail
import org.pnfolder.pnlibrary.core.lifecycle.JournalGroup
import org.pnfolder.pnlibrary.core.lifecycle.TaskControl
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletionStage

/** The state of the current update plan, as the menu shows it. */
data class PlanInfo(val state: UpdateState, val hasChanges: Boolean, val blockers: List<String>)

/** A plugin connected to the library. */
data class PluginRow(val name: String, val version: String?, val modules: Int)

/** What an event says about itself and who owns it. */
data class EventAbout(val purpose: String, val usage: String, val owner: OwnerInfo?)

/** Who asks for a chat command to be run on another server: the name and the permissions held at home. */
data class ViewerInfo(val name: String, val permissions: Set<String>)

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
    /** The library version. */
    fun version(): String

    /** The update state of every plugin that registered for updates, by name. */
    fun snapshots(): List<UpdateSnapshot>

    /** The update state of one plugin, or null when it did not register. */
    fun snapshot(product: String): UpdateSnapshot?

    /** The plugins connected to the library, registered for updates or not. */
    fun plugins(): List<PluginRow>

    /** The current update plan, or null when there is none. */
    fun plan(): PlanInfo?

    /** Until when updates of [product] are paused, or null. */
    fun heldUntil(product: String): Instant?

    /** How many entries the update history has, for one plugin or for all. */
    fun historyCount(product: String?): CompletionStage<Int>

    /** One page of the update history, from page 1. */
    fun historyPage(page: Int, product: String?): CompletionStage<List<UpdateHistoryEntry>>

    /** The saved backups. */
    fun backups(): CompletionStage<List<UpdateBackup>>

    /** Asks every catalog for new versions. */
    fun checkAll(actor: UpdateActor): CompletionStage<Boolean>

    /** Asks the catalog of one plugin for a new version; it does not wait for the answer. */
    fun checkOne(product: String, actor: UpdateActor)

    /** Switches the automatic download of [product]. */
    fun setAutomatic(product: String, enabled: Boolean, actor: UpdateActor): CompletionStage<Boolean>

    /** Sets the update channel of [product]; null returns it to the default. */
    fun setChannel(product: String, channel: UpdateChannel?, actor: UpdateActor): CompletionStage<Boolean>

    /** Pauses updates of [product] for [duration]; null lifts the pause. */
    fun setHeld(product: String, duration: Duration?, actor: UpdateActor): CompletionStage<Boolean>

    /** Prepares the rollback to a backup. */
    fun rollback(id: UUID, restoreConfigurations: Boolean, actor: UpdateActor): CompletionStage<Boolean>

    /** The journal groups of the last [hours] hours; [level] is `info`, `warning`, `error` or null. */
    fun journalGroups(level: String?, hours: Int, limit: Int): List<JournalGroup>

    /** The whole journal event behind a group, or null when it is no longer there. */
    fun journalDetail(id: String): JournalDetail?

    /** Every task of every plugin. */
    fun tasks(): List<TaskSnapshot>

    /** Starts the waiting task at once. */
    fun runTask(id: String): TaskControl

    /** Stops the task. */
    fun cancelTask(id: String): TaskControl

    /** The events of the event bus. */
    fun events(): List<EventInfo>

    /** The listeners of the event bus. */
    fun listeners(): List<ListenerInfo>

    /** The calls per day, for one event or for all. */
    fun eventHistory(eventType: String?): List<EventDay>

    /** How many days of calls are kept. */
    fun eventHistoryDays(): Int

    /** The events whose calls are being recorded. */
    fun tracedEvents(): List<String>

    /** The recorded calls. */
    fun traces(): List<EventTrace>

    /** Forgets the recorded calls. */
    fun clearTraces()

    /** Starts or stops recording the calls of an event. */
    fun setTracing(eventType: String, enabled: Boolean)

    /** Writes the call history to a file and returns where, or null. */
    fun exportEventHistory(json: Boolean): String?

    /** The purpose and the owner of an event, or null when it is unknown. */
    fun eventAbout(eventType: String): EventAbout?

    /** The plugin called [name], or null. */
    fun ownerNamed(name: String): OwnerInfo?

    /** Runs a chat command such as `pn status` for [viewer] and returns what it printed. */
    fun command(viewer: ViewerInfo, line: String): List<Component>
}
