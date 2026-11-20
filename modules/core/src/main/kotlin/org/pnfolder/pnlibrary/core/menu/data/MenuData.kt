package org.pnfolder.pnlibrary.core.menu.data

import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.config.ConfigEditResult
import org.pnfolder.pnlibrary.api.config.ConfigSetting
import org.pnfolder.pnlibrary.api.events.inspect.EventInfo
import org.pnfolder.pnlibrary.api.events.inspect.ListenerInfo
import org.pnfolder.pnlibrary.api.events.inspect.history.EventDay
import org.pnfolder.pnlibrary.api.events.inspect.trace.EventTrace
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot
import org.pnfolder.pnlibrary.api.updates.backup.UpdateBackup
import org.pnfolder.pnlibrary.api.updates.history.UpdateHistoryEntry
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel
import org.pnfolder.pnlibrary.api.updates.state.UpdateActor
import org.pnfolder.pnlibrary.api.updates.state.UpdateSnapshot
import org.pnfolder.pnlibrary.api.updates.state.UpdateState
import org.pnfolder.pnlibrary.core.commands.modules.DiskReport
import org.pnfolder.pnlibrary.core.commands.modules.LibraryRow
import org.pnfolder.pnlibrary.core.journal.JournalDetail
import org.pnfolder.pnlibrary.core.journal.JournalGroup
import org.pnfolder.pnlibrary.core.link.ServerGroups
import org.pnfolder.pnlibrary.core.menu.OwnerInfo
import org.pnfolder.pnlibrary.core.menu.screens.ConfigRow
import org.pnfolder.pnlibrary.core.placeholders.entries.PlaceholderInfo
import org.pnfolder.pnlibrary.core.stats.StatsSnapshot
import org.pnfolder.pnlibrary.core.tasks.TaskControl
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletionStage

/** State of the pending update plan and what blocks it. */
data class PlanInfo(val state: UpdateState, val hasChanges: Boolean, val blockers: List<String>)

/** One plugin line of the plugin list. */
data class PluginRow(val name: String, val version: String?, val modules: Int)

/** What an event is for, how to use it, and which plugin owns it. */
data class EventAbout(val purpose: String, val usage: String, val owner: OwnerInfo?)

/** Name and permissions of the player who looks at the menu. */
data class ViewerInfo(val name: String, val permissions: Set<String>)

/**
 * One setting of the library as the menu shows it.
 *
 * @property kind `SWITCH`, `NUMBER`, `TEXT`, `TIME` or `CHOICE`
 * @property range the allowed values in words, or empty
 * @property live whether a change starts to work at once; otherwise it waits for a restart
 * @property choiceSource the name of the list a `CHOICE` setting is chosen from; the menu builds the list itself, it is not sent
 */
data class SettingRow(
    val key: String,
    val title: String,
    val kind: String,
    val description: String,
    val current: String,
    val default: String,
    val changed: Boolean,
    val range: String,
    val examples: List<String>,
    val live: Boolean,
    val choiceSource: String = "",
)

/** What came of changing a setting from the menu: [message] is the reason when [ok] is false, otherwise the new value. */
data class SettingOutcome(val ok: Boolean, val message: String, val live: Boolean = true)

/** Kind of change the menu asks a proxy to make to server groups. */
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

/** One group change; [server] and [plugin] are set only for operations that need them. */
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

    /** Chooses the Java build of [product]; null returns it to the recommended one. */
    fun setJavaBuild(product: String, java: Int?, actor: UpdateActor): CompletionStage<Boolean>

    /** Pauses updates of [product] for [duration]; null lifts the pause. */
    fun setHeld(product: String, duration: Duration?, actor: UpdateActor): CompletionStage<Boolean>

    fun rollback(id: UUID, restoreConfigurations: Boolean, actor: UpdateActor): CompletionStage<Boolean>

    /** Puts the saved database [name] of [product] back from the set [id]; rows written since are lost. */
    fun restoreDatabase(id: UUID, product: String, name: String, actor: UpdateActor): CompletionStage<Boolean>

    /** Copies the saved files of [product] from the set [id] into a folder and returns its path. */
    fun extractBackup(id: UUID, product: String, actor: UpdateActor): CompletionStage<String>

    /** The journal groups of the last [hours] hours; [level] is `info`, `warning`, `error` or null. */
    fun journalGroups(level: String?, hours: Int, limit: Int): List<JournalGroup>

    /** The whole journal event behind a group, or null when it is no longer there. */
    fun journalDetail(id: String): JournalDetail?

    fun tasks(): List<TaskSnapshot>

    fun stats(): StatsSnapshot

    fun placeholders(): List<PlaceholderInfo>

    /** The configuration files of this server; another server's files are managed there, so the default is none. */
    fun configs(): List<ConfigRow> = emptyList()

    /** The settings of one file that may be changed in game, with their values. */
    fun configSettings(id: String): List<ConfigSetting> = emptyList()

    /** Changes one setting of a file of this server and saves it. */
    fun editConfig(id: String, path: String, text: String): ConfigEditResult =
        throw UnsupportedOperationException("Настройки меняются на самом сервере")

    /** Every setting of the library of this server with its value; empty when it has none. */
    fun settings(): List<SettingRow> = emptyList()

    /** Sets the setting [key] to [value], or returns it to the default when [value] is null. */
    fun changeSetting(key: String, value: String?, actor: UpdateActor): SettingOutcome =
        SettingOutcome(false, "Настройки меняются на самом сервере")

    /** Whether missing plugins are downloaded without a command; `null` when this server has no such download. */
    fun downloadsAutomatic(): Boolean? = null

    fun setDownloadsAutomatic(value: Boolean) {}

    /** Plugins other plugins need and the server lacks, grouped by who asks; none when nothing is missing or this server cannot say. */
    fun missingDependencies(): List<MissingPlugins> = emptyList()

    /** Downloads the group with this [code] now and says in a sentence how it went. */
    fun installDependencies(code: String): java.util.concurrent.CompletionStage<String> =
        java.util.concurrent.CompletableFuture.completedFuture("Здесь нечего скачивать.")

    /** The modules of the plugins on this server; none when this server cannot switch them. */
    fun modules(): List<ModuleInfo> = emptyList()

    /** Runs one of [ModuleInfo.ACTIONS] on the module [key] and returns how the module looks afterwards. */
    /** The libraries plugins and modules of this server registered; none when this server cannot say. */
    fun libraries(): List<LibraryRow> = emptyList()

    /** What each plugin takes on the disk of this server. */
    fun disk(): DiskReport = DiskReport.EMPTY

    fun moduleAction(key: String, action: String): ModuleInfo =
        throw UnsupportedOperationException("Модули переключаются на самом сервере")

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

    fun ownerNamed(name: String): OwnerInfo?

    fun groups(): List<ServerGroups.Group>

    /** Applies one change to the groups; returns false when there is no such group or the name is unfit. */
    fun editGroup(edit: GroupEdit): Boolean

    fun command(viewer: ViewerInfo, line: String): List<Component>
}

/**
 * One group of missing plugins, as the menu shows it.
 *
 * @property code what `/pn deps install <code>` takes
 * @property owner the plugin that asks for them
 * @property installable whether the library can download them (otherwise they are put in by hand)
 * @property lines one line per plugin: name, version, required or not
 * @property links the addresses to get them from
 */
data class MissingPlugins(
    val code: String,
    val owner: String,
    val installable: Boolean,
    val lines: List<String>,
    val links: List<String>,
)
