package org.pnfolder.pnlibrary.core.menu

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.pnfolder.pnlibrary.api.updates.UpdateActor
import org.pnfolder.pnlibrary.api.updates.UpdateChannel
import org.pnfolder.pnlibrary.core.link.DataCall
import org.pnfolder.pnlibrary.core.link.DataHandler
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executor

/**
 * Answers the questions another server asks about this one's library.
 *
 * It only reads the facts and carries out the actions of [MenuData]; it never draws anything. The server of the viewer
 * builds the menu from the answers.
 *
 * @param data the facts of this server
 * @param executor where the questions are answered, away from the thread that received them
 */
class MenuDataServer(
    private val data: MenuData,
    private val executor: Executor,
) : DataHandler {
    override fun handle(call: DataCall, reply: (Boolean, String) -> Unit) {
        executor.execute {
            try {
                answer(call, reply)
            } catch (error: Exception) {
                reply(false, error.message ?: error.javaClass.simpleName)
            }
        }
    }

    private fun answer(call: DataCall, reply: (Boolean, String) -> Unit) {
        val args = runCatching { JsonParser.parseString(call.args).asJsonObject }.getOrDefault(JsonObject())
        val actor = UpdateActor(UpdateActor.Kind.INTEGRATION, "remote:${call.origin}", "${call.viewer.ifEmpty { "игрок" }} (${call.origin})")
        fun text(name: String): String? = args.get(name)?.takeIf { !it.isJsonNull }?.asString
        fun done(stage: CompletionStage<Boolean>) {
            stage.whenComplete { value, error ->
                if (error != null) reply(false, generateSequence(error) { it.cause }.last().let { it.message ?: it.javaClass.simpleName })
                else reply(true, MenuCalls.gson.toJson(value))
            }
        }
        fun give(value: Any?) = reply(true, MenuCalls.gson.toJson(value))
        when (call.method) {
            MenuCalls.VERSION -> give(data.version())
            MenuCalls.SNAPSHOTS -> give(data.snapshots())
            MenuCalls.SNAPSHOT -> give(data.snapshot(text("product").orEmpty()))
            MenuCalls.PLUGINS -> give(data.plugins())
            MenuCalls.PLAN -> give(data.plan())
            MenuCalls.HELD_UNTIL -> give(data.heldUntil(text("product").orEmpty()))
            MenuCalls.HISTORY_COUNT -> data.historyCount(text("product")).whenComplete { value, error -> finish(reply, value, error) }
            MenuCalls.HISTORY_PAGE ->
                data.historyPage(args.get("page")?.asInt ?: 1, text("product")).whenComplete { value, error -> finish(reply, value, error) }
            MenuCalls.BACKUPS -> data.backups().whenComplete { value, error -> finish(reply, value, error) }
            MenuCalls.CHECK_ALL -> done(data.checkAll(actor))
            MenuCalls.CHECK_ONE -> {
                data.checkOne(text("product").orEmpty(), actor)
                give(true)
            }
            MenuCalls.SET_AUTOMATIC -> done(data.setAutomatic(text("product").orEmpty(), args.get("enabled")?.asBoolean == true, actor))
            MenuCalls.SET_CHANNEL -> {
                val channel = text("channel")?.let { name -> UpdateChannel.values().firstOrNull { it.name == name } }
                done(data.setChannel(text("product").orEmpty(), channel, actor))
            }
            MenuCalls.SET_HELD -> {
                val duration = args.get("millis")?.takeIf { !it.isJsonNull }?.asLong?.let(Duration::ofMillis)
                done(data.setHeld(text("product").orEmpty(), duration, actor))
            }
            MenuCalls.ROLLBACK -> done(data.rollback(UUID.fromString(text("id")), args.get("restore")?.asBoolean == true, actor))
            MenuCalls.JOURNAL_GROUPS -> give(data.journalGroups(text("level"), args.get("hours")?.asInt ?: 24, args.get("limit")?.asInt ?: 200))
            MenuCalls.JOURNAL_DETAIL -> give(data.journalDetail(text("id").orEmpty()))
            MenuCalls.TASKS -> give(data.tasks())
            MenuCalls.RUN_TASK -> give(data.runTask(text("id").orEmpty()))
            MenuCalls.CANCEL_TASK -> give(data.cancelTask(text("id").orEmpty()))
            MenuCalls.EVENTS -> give(data.events())
            MenuCalls.LISTENERS -> give(data.listeners())
            MenuCalls.EVENT_HISTORY -> give(data.eventHistory(text("type")))
            MenuCalls.EVENT_HISTORY_DAYS -> give(data.eventHistoryDays())
            MenuCalls.TRACED_EVENTS -> give(data.tracedEvents())
            MenuCalls.TRACES -> give(data.traces())
            MenuCalls.CLEAR_TRACES -> {
                data.clearTraces()
                give(true)
            }
            MenuCalls.SET_TRACING -> {
                data.setTracing(text("type").orEmpty(), args.get("enabled")?.asBoolean == true)
                give(true)
            }
            MenuCalls.EXPORT_EVENT_HISTORY -> give(data.exportEventHistory(args.get("json")?.asBoolean == true))
            MenuCalls.EVENT_ABOUT -> give(data.eventAbout(text("type").orEmpty()))
            MenuCalls.OWNER_NAMED -> give(data.ownerNamed(text("name").orEmpty()))
            MenuCalls.COMMAND -> {
                val permissions = args.getAsJsonArray("permissions")?.map { it.asString }?.toSet().orEmpty()
                val viewer = ViewerInfo(call.viewer.ifEmpty { "игрок" }, permissions)
                give(MenuCalls.encodeLines(data.command(viewer, text("line").orEmpty())))
            }
            else -> reply(false, "Неизвестный запрос: ${call.method}")
        }
    }

    private fun finish(reply: (Boolean, String) -> Unit, value: Any?, error: Throwable?) {
        if (error != null) reply(false, generateSequence(error) { it.cause }.last().let { it.message ?: it.javaClass.simpleName })
        else reply(true, MenuCalls.gson.toJson(value))
    }
}
