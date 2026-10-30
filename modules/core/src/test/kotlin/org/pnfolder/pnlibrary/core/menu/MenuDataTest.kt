package org.pnfolder.pnlibrary.core.menu

import net.kyori.adventure.text.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.events.EventDay
import org.pnfolder.pnlibrary.api.events.EventTrace
import org.pnfolder.pnlibrary.api.events.ListenerInfo
import org.pnfolder.pnlibrary.api.plugin.ModuleId
import org.pnfolder.pnlibrary.api.plugin.PluginId
import org.pnfolder.pnlibrary.api.tasks.TaskExecution
import org.pnfolder.pnlibrary.api.tasks.TaskId
import org.pnfolder.pnlibrary.api.tasks.TaskSnapshot
import org.pnfolder.pnlibrary.api.tasks.TaskStatus
import org.pnfolder.pnlibrary.api.updates.ProductId
import org.pnfolder.pnlibrary.api.updates.UpdateActor
import org.pnfolder.pnlibrary.api.updates.UpdateChannel
import org.pnfolder.pnlibrary.api.updates.UpdateSnapshot
import org.pnfolder.pnlibrary.api.updates.UpdateState
import org.pnfolder.pnlibrary.api.updates.backup.UpdateBackup
import org.pnfolder.pnlibrary.api.updates.backup.UpdateBackupComponent
import org.pnfolder.pnlibrary.api.updates.backup.UpdateHistoryEntry
import org.pnfolder.pnlibrary.api.updates.release.ProductChange
import org.pnfolder.pnlibrary.api.updates.release.ReleaseSummary
import org.pnfolder.pnlibrary.api.version.ApiVersionRange
import org.pnfolder.pnlibrary.api.version.SemanticVersion
import org.pnfolder.pnlibrary.core.lifecycle.JournalGroup
import org.pnfolder.pnlibrary.core.lifecycle.TaskControl
import org.pnfolder.pnlibrary.core.link.DataCall
import org.pnfolder.pnlibrary.core.link.DataChunks
import org.pnfolder.pnlibrary.core.link.DataClient
import org.pnfolder.pnlibrary.core.link.LinkCodec
import org.pnfolder.pnlibrary.core.link.LinkHub
import org.pnfolder.pnlibrary.core.link.ServerGroups
import java.lang.reflect.Proxy
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CompletableFuture

class MenuDataTest {
    private val key = ByteArray(32) { it.toByte() }
    private val now = Instant.parse("2026-10-30T12:00:00Z")

    private val snapshot = UpdateSnapshot(
        "shop", "1.0.0", "1.1.0", UpdateChannel.BETA, UpdateState.UPDATE_AVAILABLE, 21, 17, true, "https://example.org/r", "ok", null,
        ApiVersionRange(1, 2), listOf(ReleaseSummary("1.1.0", UpdateChannel.STABLE, now, "https://example.org/c")),
    )
    private val entry = UpdateHistoryEntry(
        UUID.randomUUID(), now, UUID.randomUUID(), UpdateState.UPDATE_STAGED, UpdateActor(UpdateActor.Kind.PLAYER, "id", "Admin"), "stage",
        listOf(ProductChange(ProductId.of("shop"), SemanticVersion.parse("1.0.0"), SemanticVersion.parse("1.1.0"))), "text",
    )
    private val backup = UpdateBackup(UUID.randomUUID(), now, UUID.randomUUID(), listOf(UpdateBackupComponent(ProductId.of("shop"), "1.1.0", "1.0.0")), 3)
    private val task = TaskSnapshot(
        TaskId("shop:save"), "save", "key", "shop", TaskExecution.Kind.ASYNC, TaskStatus.SCHEDULED, setOf("a"), now, now, 4, 1, now, now, null,
        "d", "u", "s", Duration.ofMinutes(5), Duration.ofMillis(30), cancellable = false, runnableNow = true, hidden = false,
    )
    private val listener = ListenerInfo(PluginId.of("shop"), ModuleId.of("core"), "a.b.Event", 3, true, "x", 10, 1, 500, 900)
    private val trace = EventTrace("a.b.Event", true, "ok", listOf(EventTrace.Step(PluginId.of("shop"), null, 1, false, false, 5, "a", "b")), at = now)

    private fun <T> roundTrip(value: T, type: java.lang.reflect.Type): T = MenuCalls.gson.fromJson(MenuCalls.gson.toJson(value), type)

    @Test
    fun `update data survives the trip as json`() {
        val back = roundTrip(listOf(snapshot), MenuCalls.typeOf<List<UpdateSnapshot>>()).single()
        assertEquals("shop", back.product)
        assertEquals(UpdateState.UPDATE_AVAILABLE, back.state)
        assertEquals(ApiVersionRange(1, 2), back.supportedApi)
        assertEquals(now, back.availableReleases.single().publishedAt)

        val history = roundTrip(listOf(entry), MenuCalls.typeOf<List<UpdateHistoryEntry>>()).single()
        assertEquals(entry.id, history.id)
        assertEquals("shop", history.changes.single().product.value)
        assertEquals(SemanticVersion.parse("1.1.0"), history.changes.single().to)
        assertEquals("Admin", history.actor.name)

        val saved = roundTrip(listOf(backup), MenuCalls.typeOf<List<UpdateBackup>>()).single()
        assertEquals("1.0.0", saved.components.single().backupVersion)
        assertEquals(3, saved.configurationFiles)
    }

    @Test
    fun `task and event data survive the trip as json`() {
        val tasks = roundTrip(listOf(task), MenuCalls.typeOf<List<TaskSnapshot>>()).single()
        assertEquals("shop:save", tasks.id.value)
        assertEquals(Duration.ofMinutes(5), tasks.interval)
        assertEquals(setOf("a"), tasks.tags)
        assertEquals(false, tasks.cancellable)

        val listeners = roundTrip(listOf(listener), MenuCalls.typeOf<List<ListenerInfo>>()).single()
        assertEquals("shop", listeners.plugin.value)
        assertEquals("core", listeners.module?.value)
        assertEquals(900, listeners.maxNanos)

        val traces = roundTrip(listOf(trace), MenuCalls.typeOf<List<EventTrace>>()).single()
        assertEquals("shop", traces.steps.single().plugin.value)
        assertEquals(now, traces.at)

        val day = roundTrip(listOf(EventDay(LocalDate.of(2026, 10, 30), "a.b.Event", 5, 1)), MenuCalls.typeOf<List<EventDay>>()).single()
        assertEquals(LocalDate.of(2026, 10, 30), day.date)

        val about = roundTrip(EventAbout("p", "u", OwnerInfo("shop", "1.0", listOf("me"))), MenuCalls.typeOf<EventAbout?>())
        assertEquals(listOf("me"), about?.owner?.authors)

        val plan = roundTrip(PlanInfo(UpdateState.BLOCKED, true, listOf("busy")), MenuCalls.typeOf<PlanInfo?>())
        assertEquals(listOf("busy"), plan?.blockers)
    }

    /** A server whose facts are canned, and whose answers travel through the real link code. */
    private fun connected(vararg answers: Pair<String, Any?>): RemoteMenuData {
        val canned = answers.toMap()
        val seen = mutableListOf<String>()
        val data = Proxy.newProxyInstance(MenuData::class.java.classLoader, arrayOf(MenuData::class.java)) { _, method, args ->
            seen += method.name
            val value = canned[method.name]
            when {
                method.name == "command" -> listOf(Component.text("printed ${args!![1]}"))
                method.returnType == java.util.concurrent.CompletionStage::class.java -> CompletableFuture.completedFuture(value)
                method.returnType == Void.TYPE -> null
                else -> value
            }
        } as MenuData
        val server = MenuDataServer(data) { it.run() }
        val client = DataClient()
        LinkHub.key = key
        LinkHub.uplink = { bytes ->
            val call: DataCall = LinkCodec.decodeCall(bytes, key)!!
            server.handle(call) { ok, text ->
                DataChunks.split(call.id, "lobby", ok, text, key)!!.forEach { client.accept(LinkCodec.decodeChunk(it, key)!!) }
            }
            true
        }
        return RemoteMenuData("games", client)
    }

    private fun <T> over(vararg answers: Pair<String, Any?>, check: (RemoteMenuData) -> T): T {
        val remote = connected(*answers)
        try {
            return check(remote)
        } finally {
            LinkHub.uplink = null
            LinkHub.key = null
        }
    }

    @Test
    fun `the menu of another server reads the facts and runs its actions through the link`() {
        over(
            "snapshots" to listOf(snapshot),
            "plan" to PlanInfo(UpdateState.UPDATE_STAGED, true, listOf("busy")),
            "journalGroups" to listOf(JournalGroup("g1", "error", "shop", "boom", 3, 1_000L)),
            "runTask" to TaskControl.FORBIDDEN,
            "historyCount" to 7,
            "checkAll" to true,
        ) { remote ->
            assertEquals("shop", remote.snapshots().single().product)
            assertEquals(listOf("busy"), remote.plan()?.blockers)
            assertEquals("boom", remote.journalGroups("error", 24, 10).single().message)
            assertEquals(TaskControl.FORBIDDEN, remote.runTask("x"))
            assertEquals(7, remote.historyCount(null).toCompletableFuture().get())
            assertTrue(remote.checkAll(UpdateActor(UpdateActor.Kind.PLAYER, "id", "Admin")).toCompletableFuture().get())
            assertNull(remote.snapshot("none"))
        }
    }

    @Test
    fun `groups with the differences of their servers travel through the link and are edited there`() {
        val group = ServerGroups.Group("lobby", setOf("l1", "l2"), setOf("shop", "chat"), mapOf("l2" to setOf("quiz")), mapOf("l1" to setOf("chat")))
        over("groups" to listOf(group), "editGroup" to true) { remote ->
            val back = remote.groups().single()
            assertEquals(setOf("shop"), back.wanted("l1"))
            assertEquals(setOf("shop", "chat", "quiz"), back.wanted("l2"))
            assertTrue(remote.editGroup(GroupEdit(GroupOp.SKIP, "lobby", "l2", "chat")))
        }
    }

    @Test
    fun `a command runs on the other server and its answer is printed to the viewer`() {
        over { remote ->
            val lines = remote.command(ViewerInfo("Admin", setOf("pnlibrary.admin")), "pn events full g1")
            assertEquals(listOf(Component.text("printed pn events full g1")), lines)
        }
    }
}
