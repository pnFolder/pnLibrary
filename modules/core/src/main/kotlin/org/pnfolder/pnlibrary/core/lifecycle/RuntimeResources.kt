package org.pnfolder.pnlibrary.core.lifecycle

import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.commands.CommandBuilder
import org.pnfolder.pnlibrary.api.commands.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.CommandRegistration
import org.pnfolder.pnlibrary.api.commands.command
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.api.tasks.TaskScope
import org.pnfolder.pnlibrary.api.tasks.TaskSnapshot
import org.pnfolder.pnlibrary.core.journal.JournalDetail
import org.pnfolder.pnlibrary.core.journal.JournalGroup
import org.pnfolder.pnlibrary.core.stats.StatsSnapshot
import org.pnfolder.pnlibrary.core.tasks.TaskControl

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

    /** The numbers of the server right now: tasks, events, and the errors and warnings of the day. */
    fun statsSnapshot(): StatsSnapshot

    /** Stops the task with this id for an administrator; the task may forbid it. */
    fun cancelTask(id: String): TaskControl

    /** Starts the waiting task with this id at once for an administrator; the task may forbid it. */
    fun runTaskNow(id: String): TaskControl

    /** The whole event behind a [JournalGroup.id], or `null` when it is no longer in the journal. */
    fun journalDetail(id: String): JournalDetail?
}

/** Every live and retained task of every plugin; empty when the runtime has no task service. */
fun PnLibrary.taskSnapshots(): List<TaskSnapshot> = (this as? RuntimeResources)?.taskSnapshots().orEmpty()

/** Stops the task with this id for an administrator; the task may forbid it. */
fun PnLibrary.cancelTask(id: String): TaskControl = (this as? RuntimeResources)?.cancelTask(id) ?: TaskControl.UNAVAILABLE

/** Starts the waiting task with this id at once for an administrator; the task may forbid it. */
fun PnLibrary.runTaskNow(id: String): TaskControl = (this as? RuntimeResources)?.runTaskNow(id) ?: TaskControl.UNAVAILABLE

/** The whole journal event with the id [id]; `null` when there is none or the runtime has no journal. */
fun PnLibrary.journalDetail(id: String): JournalDetail? = (this as? RuntimeResources)?.journalDetail(id)

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

/** The numbers of the server right now. */
fun PnLibrary.statsSnapshot(): StatsSnapshot = runtimeResources().statsSnapshot()

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
