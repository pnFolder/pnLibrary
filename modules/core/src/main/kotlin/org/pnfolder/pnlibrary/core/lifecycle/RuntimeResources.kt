package org.pnfolder.pnlibrary.core.lifecycle

import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.commands.CommandBuilder
import org.pnfolder.pnlibrary.api.commands.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.CommandRegistration
import org.pnfolder.pnlibrary.api.commands.command
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.api.tasks.TaskScope
import org.pnfolder.pnlibrary.api.tasks.TaskSnapshot

/** Private runtime capabilities, deliberately absent from the consumer API facade. */
internal interface RuntimeResources {
    val runtimeTasks: TaskScope
    fun registerRuntimeCommand(command: CommandDefinition): CommandRegistration

    /** A shared command the platform puts under its own control command (`events`, `debug`), or `null`. */
    fun mountableCommand(name: String): CommandDefinition?

    /** The notice for an administrator who has just joined: recent problems from the journal, or nothing. */
    fun journalJoinNotice(): List<Component>

    /** Journal records of the last [sinceHours] hours, equal events merged, newest first; [level] is `info`, `warning`, `error` or `null`. */
    fun journalGroups(level: String?, sinceHours: Int, limit: Int): List<JournalGroup>

    /** Every live and retained task of every plugin. */
    fun taskSnapshots(): List<TaskSnapshot>

    /** Cancels the task with this id and reports whether it was still active. */
    fun cancelTask(id: String): Boolean

    /** The whole event behind a [JournalGroup.id], or `null` when it is no longer in the journal. */
    fun journalDetail(id: String): JournalDetail?
}

/**
 * One journal event with everything the journal knows about it, for a menu.
 *
 * @property id the id that `/pn events view <id>` opens
 * @property level `info`, `warning` or `error`
 * @property source plugin or component that wrote the event
 * @property message the whole text of the newest repeat
 * @property stackTrace the stack trace with its causes, or `null` when the event has none
 * @property details other facts the event carries, such as a player or a version
 * @property times when it happened, newest first, in epoch milliseconds
 */
data class JournalDetail(
    val id: String,
    val level: String,
    val source: String,
    val message: String,
    val stackTrace: String?,
    val details: Map<String, String>,
    val times: List<Long>,
)

/** Every live and retained task of every plugin; empty when the runtime has no task service. */
fun PnLibrary.taskSnapshots(): List<TaskSnapshot> = (this as? RuntimeResources)?.taskSnapshots().orEmpty()

/** Cancels the task with this id and reports whether it was still active. */
fun PnLibrary.cancelTask(id: String): Boolean = (this as? RuntimeResources)?.cancelTask(id) ?: false

/** The whole journal event with the id [id]; `null` when there is none or the runtime has no journal. */
fun PnLibrary.journalDetail(id: String): JournalDetail? = (this as? RuntimeResources)?.journalDetail(id)

/**
 * Equal journal events merged into one line, for menus.
 *
 * @property id the id that `/pn events view <id>` opens
 * @property level `info`, `warning` or `error`
 * @property source plugin or component that wrote the event
 * @property message the first line of the event text
 * @property count how many times it happened in the window
 * @property lastMillis when it happened last, in epoch milliseconds
 */
data class JournalGroup(
    val id: String,
    val level: String,
    val source: String,
    val message: String,
    val count: Int,
    val lastMillis: Long,
)

/** Journal events of the last [sinceHours] hours for a menu; empty when the runtime has no journal. */
fun PnLibrary.journalGroups(level: String? = null, sinceHours: Int = 24, limit: Int = 200): List<JournalGroup> =
    (this as? RuntimeResources)?.journalGroups(level, sinceHours, limit).orEmpty()

/**
 * Native pnLibrary integration only. Consumer plugins obtain tasks through ModuleContext.tasks.
 * This bridge belongs to core, not to the published public API.
 */
fun PnLibrary.runtimeTaskScope(): TaskScope = runtimeResources().runtimeTasks

/** Lines to show an administrator on joining, empty when the journal has nothing to report or the notice is off. */
fun PnLibrary.journalJoinNotice(): List<Component> = runtimeResources().journalJoinNotice()

/** Registers a pnLibrary-owned command from core or a bundled native integration. */
fun PnLibrary.registerRuntimeCommand(command: CommandDefinition): CommandRegistration =
    runtimeResources().registerRuntimeCommand(command)

/**
 * A shared command that this platform does not register on its own: the platform mounts it under its control
 * command instead (`events` and `debug` under Bukkit `/pn`). `null` where the command is registered separately.
 */
fun PnLibrary.mountableCommand(name: String): CommandDefinition? = (this as? RuntimeResources)?.mountableCommand(name)

/** Builds a pnLibrary-owned command; consumer plugins use ModuleContext.registerCommand. */
fun PnLibrary.registerRuntimeCommand(
    name: String,
    configure: CommandBuilder.() -> Unit,
): CommandRegistration = registerRuntimeCommand(command(name, configure))

/** Only the core runtime implements [RuntimeResources]; any other [PnLibrary] is a wiring error. */
private fun PnLibrary.runtimeResources(): RuntimeResources =
    this as? RuntimeResources ?: throw IllegalStateException(
        "Runtime resources are available only on the pnLibrary core runtime, not on ${javaClass.name}",
    )
