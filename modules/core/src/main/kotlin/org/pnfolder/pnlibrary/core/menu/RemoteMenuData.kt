package org.pnfolder.pnlibrary.core.menu

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonDeserializer
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializer
import com.google.gson.reflect.TypeToken
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import org.pnfolder.pnlibrary.api.events.EventDay
import org.pnfolder.pnlibrary.api.events.EventInfo
import org.pnfolder.pnlibrary.api.events.EventTrace
import org.pnfolder.pnlibrary.api.events.ListenerInfo
import org.pnfolder.pnlibrary.api.tasks.TaskSnapshot
import org.pnfolder.pnlibrary.api.updates.UpdateActor
import org.pnfolder.pnlibrary.api.updates.UpdateChannel
import org.pnfolder.pnlibrary.api.updates.UpdateSnapshot
import org.pnfolder.pnlibrary.api.updates.backup.UpdateBackup
import org.pnfolder.pnlibrary.api.updates.backup.UpdateHistoryEntry
import org.pnfolder.pnlibrary.core.lifecycle.JournalDetail
import org.pnfolder.pnlibrary.core.lifecycle.JournalGroup
import org.pnfolder.pnlibrary.core.lifecycle.TaskControl
import org.pnfolder.pnlibrary.core.link.DataClient
import org.pnfolder.pnlibrary.core.link.DataException
import org.pnfolder.pnlibrary.core.link.LinkHub
import org.pnfolder.pnlibrary.core.link.ServerGroups
import java.lang.reflect.Type
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

/** The names of the questions one server asks another about its library, and how their data travels as JSON. */
object MenuCalls {
    const val VERSION = "version"
    const val SNAPSHOTS = "snapshots"
    const val SNAPSHOT = "snapshot"
    const val PLUGINS = "plugins"
    const val PLAN = "plan"
    const val HELD_UNTIL = "heldUntil"
    const val HISTORY_COUNT = "historyCount"
    const val HISTORY_PAGE = "historyPage"
    const val BACKUPS = "backups"
    const val CHECK_ALL = "checkAll"
    const val CHECK_ONE = "checkOne"
    const val SET_AUTOMATIC = "setAutomatic"
    const val SET_CHANNEL = "setChannel"
    const val SET_HELD = "setHeld"
    const val ROLLBACK = "rollback"
    const val JOURNAL_GROUPS = "journalGroups"
    const val JOURNAL_DETAIL = "journalDetail"
    const val TASKS = "tasks"
    const val RUN_TASK = "runTask"
    const val CANCEL_TASK = "cancelTask"
    const val EVENTS = "events"
    const val LISTENERS = "listeners"
    const val EVENT_HISTORY = "eventHistory"
    const val EVENT_HISTORY_DAYS = "eventHistoryDays"
    const val TRACED_EVENTS = "tracedEvents"
    const val TRACES = "traces"
    const val CLEAR_TRACES = "clearTraces"
    const val SET_TRACING = "setTracing"
    const val EXPORT_EVENT_HISTORY = "exportEventHistory"
    const val EVENT_ABOUT = "eventAbout"
    const val OWNER_NAMED = "ownerNamed"
    const val COMMAND = "command"
    const val GROUPS = "groups"
    const val EDIT_GROUP = "editGroup"

    /** The calls that change nothing, whose answers may be remembered for a moment. */
    val READS = setOf(
        GROUPS,
        VERSION, SNAPSHOTS, SNAPSHOT, PLUGINS, PLAN, HELD_UNTIL, HISTORY_COUNT, HISTORY_PAGE, BACKUPS, JOURNAL_GROUPS, JOURNAL_DETAIL, TASKS,
        EVENTS, LISTENERS, EVENT_HISTORY, EVENT_HISTORY_DAYS, TRACED_EVENTS, TRACES, EVENT_ABOUT, OWNER_NAMED,
    )

    /** The JSON of the library's data types. Times travel as text, durations as milliseconds. */
    val gson: Gson = GsonBuilder()
        .registerTypeAdapter(Instant::class.java, JsonSerializer<Instant> { value, _, _ -> JsonPrimitive(value.toString()) })
        .registerTypeAdapter(Instant::class.java, JsonDeserializer { json, _, _ -> Instant.parse(json.asString) })
        .registerTypeAdapter(Duration::class.java, JsonSerializer<Duration> { value, _, _ -> JsonPrimitive(value.toMillis()) })
        .registerTypeAdapter(Duration::class.java, JsonDeserializer { json, _, _ -> Duration.ofMillis(json.asLong) })
        .registerTypeAdapter(LocalDate::class.java, JsonSerializer<LocalDate> { value, _, _ -> JsonPrimitive(value.toString()) })
        .registerTypeAdapter(LocalDate::class.java, JsonDeserializer { json, _, _ -> LocalDate.parse(json.asString) })
        .create()

    private val chat = GsonComponentSerializer.gson()

    /** Chat messages as JSON texts. */
    fun encodeLines(lines: List<Component>): List<String> = lines.map(chat::serialize)

    /** The chat messages in [texts]; one that cannot be read is skipped. */
    fun decodeLines(texts: List<String>): List<Component> = texts.mapNotNull { runCatching { chat.deserialize(it) }.getOrNull() }

    /** The type token for [T], which keeps the element type of a list. */
    inline fun <reified T> typeOf(): Type = object : TypeToken<T>() {}.type
}

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
    private val client: DataClient = LinkHub.data,
) : MenuData {
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

    override fun setHeld(product: String, duration: Duration?, actor: UpdateActor): CompletionStage<Boolean> =
        later { ask<Boolean>(MenuCalls.SET_HELD, args("product" to product, "millis" to duration?.toMillis()), actor.name) }

    override fun rollback(id: UUID, restoreConfigurations: Boolean, actor: UpdateActor): CompletionStage<Boolean> =
        later { ask<Boolean>(MenuCalls.ROLLBACK, args("id" to id.toString(), "restore" to restoreConfigurations), actor.name) }

    override fun journalGroups(level: String?, hours: Int, limit: Int): List<JournalGroup> =
        ask(MenuCalls.JOURNAL_GROUPS, args("level" to level, "hours" to hours, "limit" to limit))

    override fun journalDetail(id: String): JournalDetail? = ask(MenuCalls.JOURNAL_DETAIL, args("id" to id))

    override fun tasks(): List<TaskSnapshot> = ask(MenuCalls.TASKS)

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

    override fun groups(): List<ServerGroups.Group> = ask(MenuCalls.GROUPS)

    override fun editGroup(edit: GroupEdit): Boolean =
        ask(MenuCalls.EDIT_GROUP, args("op" to edit.op.name, "group" to edit.group, "server" to edit.server, "plugin" to edit.plugin))

    override fun command(viewer: ViewerInfo, line: String): List<Component> {
        val permissions = JsonArray().also { array -> viewer.permissions.forEach { array.add(it) } }
        val lines = ask<List<String>>(MenuCalls.COMMAND, args("line" to line).also { it.add("permissions", permissions) }, viewer.name)
        return MenuCalls.decodeLines(lines)
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
        return MenuCalls.gson.fromJson(json, MenuCalls.typeOf<T>())
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
