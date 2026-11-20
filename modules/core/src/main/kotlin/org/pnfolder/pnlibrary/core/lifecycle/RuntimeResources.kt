package org.pnfolder.pnlibrary.core.lifecycle

import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.CommandRegistration
import org.pnfolder.pnlibrary.api.commands.definition.CommandBuilder
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.definition.command
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.api.tasks.TaskScope
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot
import org.pnfolder.pnlibrary.core.downloads.fetch.DirectDownloads
import org.pnfolder.pnlibrary.core.journal.JournalDetail
import org.pnfolder.pnlibrary.core.journal.JournalGroup
import org.pnfolder.pnlibrary.core.placeholders.entries.PlaceholderInfo
import org.pnfolder.pnlibrary.core.settings.SettingPickerHook
import org.pnfolder.pnlibrary.core.settings.SettingsStore
import org.pnfolder.pnlibrary.core.stats.StatsSnapshot
import org.pnfolder.pnlibrary.core.tasks.TaskControl
import java.util.concurrent.CompletionStage

object MountableCommands {
    const val EVENTS = "events"
    const val DEBUG = "debug"
    const val STATS = "stats"
    const val PLACEHOLDERS = "placeholders"
    const val SETTINGS = "settings"

    val ALL = listOf(EVENTS, DEBUG, STATS, PLACEHOLDERS, SETTINGS)
}

internal interface RuntimeResources {
    val runtimeTasks: TaskScope
    fun registerRuntimeCommand(command: CommandDefinition): CommandRegistration
    fun mountableCommand(name: String): CommandDefinition?
    fun dispatchCommand(command: CommandDefinition, context: CommandContext): CompletionStage<Void>?
    fun journalJoinNotice(): List<Component>
    fun journalGroups(level: String?, sinceHours: Int, limit: Int): List<JournalGroup>
    fun taskSnapshots(): List<TaskSnapshot>
    fun statsSnapshot(): StatsSnapshot
    fun placeholderInfos(): List<PlaceholderInfo>
    fun cancelTask(id: String): TaskControl
    fun runTaskNow(id: String): TaskControl
    fun dependencyDownloads(): DirectDownloads?
    fun librarySettings(): SettingsStore?
    val settingPicker: SettingPickerHook
    fun journalDetail(id: String): JournalDetail?
}

internal fun PnLibrary.dependencyDownloads(): DirectDownloads? = resources()?.dependencyDownloads()
fun PnLibrary.settingPicker(): SettingPickerHook? = resources()?.settingPicker
internal fun PnLibrary.librarySettings(): SettingsStore? = resources()?.librarySettings()
fun PnLibrary.taskSnapshots(): List<TaskSnapshot> = resources()?.taskSnapshots().orEmpty()
fun PnLibrary.cancelTask(id: String): TaskControl = resources()?.cancelTask(id) ?: TaskControl.UNAVAILABLE
fun PnLibrary.runTaskNow(id: String): TaskControl = resources()?.runTaskNow(id) ?: TaskControl.UNAVAILABLE
fun PnLibrary.journalDetail(id: String): JournalDetail? = resources()?.journalDetail(id)
fun PnLibrary.journalGroups(level: String? = null, sinceHours: Int = 24, limit: Int = 200): List<JournalGroup> =
    resources()?.journalGroups(level, sinceHours, limit).orEmpty()

// Consumer plugins get tasks through ModuleContext.tasks — this bridge is for core only.
fun PnLibrary.runtimeTaskScope(): TaskScope = runtimeResources().runtimeTasks

fun PnLibrary.journalJoinNotice(): List<Component> = runtimeResources().journalJoinNotice()
fun PnLibrary.statsSnapshot(): StatsSnapshot = runtimeResources().statsSnapshot()
fun PnLibrary.placeholderInfos(): List<PlaceholderInfo> = resources()?.placeholderInfos().orEmpty()
fun PnLibrary.registerRuntimeCommand(command: CommandDefinition): CommandRegistration =
    runtimeResources().registerRuntimeCommand(command)
fun PnLibrary.mountableCommand(name: String): CommandDefinition? = resources()?.mountableCommand(name)
fun PnLibrary.dispatchRuntimeCommand(command: CommandDefinition, context: CommandContext): CompletionStage<Void>? =
    resources()?.dispatchCommand(command, context)
fun PnLibrary.registerRuntimeCommand(
    name: String,
    configure: CommandBuilder.() -> Unit,
): CommandRegistration = registerRuntimeCommand(command(name, configure))

private fun PnLibrary.resources(): RuntimeResources? = this as? RuntimeResources
private fun PnLibrary.runtimeResources(): RuntimeResources =
    resources() ?: throw IllegalStateException(
        "Runtime resources are available only on the pnLibrary core runtime, not on ${javaClass.name}",
    )
