package org.pnfolder.pnlibrary.core.menu.data

import com.google.gson.JsonObject
import net.kyori.adventure.text.Component
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
import org.pnfolder.pnlibrary.core.commands.modules.DiskReport
import org.pnfolder.pnlibrary.core.commands.modules.LibraryRow
import org.pnfolder.pnlibrary.core.journal.JournalDetail
import org.pnfolder.pnlibrary.core.journal.JournalGroup
import org.pnfolder.pnlibrary.core.link.ServerGroups
import org.pnfolder.pnlibrary.core.link.remote.DataClient
import org.pnfolder.pnlibrary.core.link.remote.DataException
import org.pnfolder.pnlibrary.core.menu.OwnerInfo
import org.pnfolder.pnlibrary.core.menu.style.HISTORY_PAGE
import org.pnfolder.pnlibrary.core.menu.style.PLACEHOLDERS
import org.pnfolder.pnlibrary.core.menu.style.ROLLBACK
import org.pnfolder.pnlibrary.core.menu.style.STATS
import org.pnfolder.pnlibrary.core.menu.style.TASKS
import org.pnfolder.pnlibrary.core.placeholders.entries.PlaceholderInfo
import org.pnfolder.pnlibrary.core.stats.StatsSnapshot
import org.pnfolder.pnlibrary.core.tasks.TaskControl
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

/**
 * The library of another server, reached over the link.
 *
 * The questions go to [target] (a game server or the proxy) and the answers come back as JSON. They are waited for, so
 * the menu calls this away from the server thread. Read answers are remembered for a few seconds, because one screen
 * asks for the same facts several times.
 *
 * @param target the server asked, as the proxy knows it
 * @param client where the questions go
 */
class RemoteMenuData(
    private val target: String,
    private val client: DataClient,
) : MenuData {
    /** Cached remote response with the time it was received. */
    private class Remembered(val at: Long, val json: String)

    private val remembered = ConcurrentHashMap<String, Remembered>()

    override fun version(): String = ask(MenuCalls.VERSION)

    override fun snapshots(): List<UpdateSnapshot> = ask(MenuCalls.SNAPSHOTS)

    override fun snapshot(product: String): UpdateSnapshot? = ask(MenuCalls.SNAPSHOT, args("product" to product))

    override fun plugins(): List<PluginRow> = ask(MenuCalls.PLUGINS)

    override fun plan(): PlanInfo? = ask(MenuCalls.PLAN)

    override fun heldUntil(product: String): Instant? = ask(MenuCalls.HELD_UNTIL, args("product" to product))

    override fun historyCount(product: String?): CompletionStage<Int> = later { ask<Int>(MenuCalls.HISTORY_COUNT, args("product" to product)) }

    override fun historyPage(page: Int, product: String?): CompletionStage<List<UpdateHistoryEntry>> =
        later { ask<List<UpdateHistoryEntry>>(MenuCalls.HISTORY_PAGE, args("page" to page, "product" to product)) }

    override fun backups(): CompletionStage<List<UpdateBackup>> = later { ask<List<UpdateBackup>>(MenuCalls.BACKUPS) }

    override fun checkAll(actor: UpdateActor): CompletionStage<Boolean> = later { ask<Boolean>(MenuCalls.CHECK_ALL, viewer = actor.name) }

    override fun checkOne(product: String, actor: UpdateActor) {
        ask<Boolean>(MenuCalls.CHECK_ONE, args("product" to product), actor.name)
    }

    override fun setAutomatic(product: String, enabled: Boolean, actor: UpdateActor): CompletionStage<Boolean> =
        later { ask<Boolean>(MenuCalls.SET_AUTOMATIC, args("product" to product, "enabled" to enabled), actor.name) }

    override fun setChannel(product: String, channel: UpdateChannel?, actor: UpdateActor): CompletionStage<Boolean> =
        later { ask<Boolean>(MenuCalls.SET_CHANNEL, args("product" to product, "channel" to channel?.name), actor.name) }

    override fun setJavaBuild(product: String, java: Int?, actor: UpdateActor): CompletionStage<Boolean> =
        later { ask<Boolean>(MenuCalls.SET_JAVA_BUILD, args("product" to product, "java" to java), actor.name) }

    override fun setHeld(product: String, duration: Duration?, actor: UpdateActor): CompletionStage<Boolean> =
        later { ask<Boolean>(MenuCalls.SET_HELD, args("product" to product, "millis" to duration?.toMillis()), actor.name) }

    override fun extractBackup(id: UUID, product: String, actor: UpdateActor): CompletionStage<String> =
        later { ask<String>(MenuCalls.EXTRACT_BACKUP, args("id" to id.toString(), "product" to product), actor.name) }

    override fun restoreDatabase(id: UUID, product: String, name: String, actor: UpdateActor): CompletionStage<Boolean> =
        later { ask<Boolean>(MenuCalls.RESTORE_DATABASE, args("id" to id.toString(), "product" to product, "name" to name), actor.name) }

    override fun rollback(id: UUID, restoreConfigurations: Boolean, actor: UpdateActor): CompletionStage<Boolean> =
        later { ask<Boolean>(MenuCalls.ROLLBACK, args("id" to id.toString(), "restore" to restoreConfigurations), actor.name) }

    override fun journalGroups(level: String?, hours: Int, limit: Int): List<JournalGroup> =
        ask(MenuCalls.JOURNAL_GROUPS, args("level" to level, "hours" to hours, "limit" to limit))

    override fun journalDetail(id: String): JournalDetail? = ask(MenuCalls.JOURNAL_DETAIL, args("id" to id))

    override fun tasks(): List<TaskSnapshot> = ask(MenuCalls.TASKS)

    override fun modules(): List<ModuleInfo> = ask(MenuCalls.MODULES)

    override fun libraries(): List<LibraryRow> = ask(MenuCalls.LIBRARIES)

    override fun disk(): DiskReport = ask(MenuCalls.DISK)

    override fun moduleAction(key: String, action: String): ModuleInfo =
        ask(MenuCalls.MODULE_ACTION, args("key" to key, "action" to action))

    override fun stats(): StatsSnapshot = ask(MenuCalls.STATS)

    override fun placeholders(): List<PlaceholderInfo> = ask(MenuCalls.PLACEHOLDERS)

    override fun runTask(id: String): TaskControl = ask(MenuCalls.RUN_TASK, args("id" to id))

    override fun cancelTask(id: String): TaskControl = ask(MenuCalls.CANCEL_TASK, args("id" to id))

    override fun events(): List<EventInfo> = ask(MenuCalls.EVENTS)

    override fun listeners(): List<ListenerInfo> = ask(MenuCalls.LISTENERS)

    override fun eventHistory(eventType: String?): List<EventDay> = ask(MenuCalls.EVENT_HISTORY, args("type" to eventType))

    override fun eventHistoryDays(): Int = ask(MenuCalls.EVENT_HISTORY_DAYS)

    override fun tracedEvents(): List<String> = ask(MenuCalls.TRACED_EVENTS)

    override fun traces(): List<EventTrace> = ask(MenuCalls.TRACES)

    override fun clearTraces() {
        ask<Boolean>(MenuCalls.CLEAR_TRACES)
    }

    override fun setTracing(eventType: String, enabled: Boolean) {
        ask<Boolean>(MenuCalls.SET_TRACING, args("type" to eventType, "enabled" to enabled))
    }

    override fun exportEventHistory(json: Boolean): String? = ask(MenuCalls.EXPORT_EVENT_HISTORY, args("json" to json))

    override fun eventAbout(eventType: String): EventAbout? = ask(MenuCalls.EVENT_ABOUT, args("type" to eventType))

    override fun ownerNamed(name: String): OwnerInfo? = ask(MenuCalls.OWNER_NAMED, args("name" to name))

    override fun settings(): List<SettingRow> = ask(MenuCalls.SETTINGS)

    override fun changeSetting(key: String, value: String?, actor: UpdateActor): SettingOutcome =
        ask(MenuCalls.CHANGE_SETTING, args("key" to key, "value" to value), actor.name)

    override fun groups(): List<ServerGroups.Group> = ask(MenuCalls.GROUPS)

    override fun editGroup(edit: GroupEdit): Boolean =
        ask(MenuCalls.EDIT_GROUP, args("op" to edit.op.name, "group" to edit.group, "server" to edit.server, "plugin" to edit.plugin))

    override fun command(viewer: ViewerInfo, line: String): List<Component> {
        val lines = ask<List<String>>(MenuCalls.COMMAND, args("line" to line), viewer.name)
        return MenuWire.decodeLines(lines)
    }

    private fun args(vararg pairs: Pair<String, Any?>): JsonObject = JsonObject().also { json ->
        pairs.forEach { (name, value) ->
            when (value) {
                null -> Unit
                is String -> json.addProperty(name, value)
                is Number -> json.addProperty(name, value)
                is Boolean -> json.addProperty(name, value)
                else -> json.addProperty(name, value.toString())
            }
        }
    }

    private fun <T> later(work: () -> T): CompletionStage<T> {
        val result = CompletableFuture<T>()
        Thread({ runCatching(work).onSuccess { result.complete(it) }.onFailure { result.completeExceptionally(it) } }, "pnlibrary-remote-menu").apply {
            isDaemon = true
        }.start()
        return result
    }

    private inline fun <reified T> ask(method: String, args: JsonObject = JsonObject(), viewer: String = ""): T {
        val reads = method in MenuCalls.READS
        val key = "$method $args"
        val now = System.nanoTime()
        val json = remembered[key]?.takeIf { reads && now - it.at < REMEMBER_NANOS }?.json ?: wait(method, args, viewer).also {
            if (reads) remembered[key] = Remembered(now, it) else remembered.clear()
        }
        return MenuWire.gson.fromJson(json, MenuWire.typeOf<T>())
    }

    private fun wait(method: String, args: JsonObject, viewer: String): String = try {
        client.call(target, viewer, method, args.toString()).get(WAIT_SECONDS, TimeUnit.SECONDS)
    } catch (error: ExecutionException) {
        throw (error.cause as? DataException ?: DataException(error.cause?.message ?: error.javaClass.simpleName))
    } catch (error: java.util.concurrent.TimeoutException) {
        throw DataException("Сервер не ответил.")
    }

    private companion object {
        const val WAIT_SECONDS = 25L
        const val REMEMBER_NANOS = 3_000_000_000L
    }
}
