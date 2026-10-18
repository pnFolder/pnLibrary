package ru.privatenull.pnlibrary.core.updates

import net.kyori.adventure.text.Component
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.commands.CommandContext
import ru.privatenull.pnlibrary.api.commands.CommandSender
import ru.privatenull.pnlibrary.api.plugin.PluginDependency
import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import ru.privatenull.pnlibrary.api.updates.*
import ru.privatenull.pnlibrary.core.commands.CommandRoute
import ru.privatenull.pnlibrary.core.commands.CommandTreeRouter
import java.lang.reflect.Proxy
import java.time.Duration
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletableFuture

class ProxyUpdateCommandTest {
    @Test
    fun `explicit plan ID is not silently replaced by the current plan`() {
        val updates = Updates()
        val requested = UUID.randomUUID()
        execute(updates, Sender(), "stage", requested.toString())
        assertEquals(requested, updates.staged)
        assertEquals(UpdateActor.Kind.CONSOLE, updates.actor?.kind)
        assertNotEquals(updates.current.id, updates.staged)
    }

    @Test
    fun `history page is parsed and non console callers cannot read it`() {
        val updates = Updates()
        execute(updates, Sender(), "history", "2")
        assertEquals(listOf(2), updates.pages)
        execute(updates, Sender(isConsole = false), "history", "3")
        execute(updates, Sender(), "history", "0")
        assertEquals(listOf(2), updates.pages)
    }

    @Test
    fun `settings are changed by the console with an attributed actor`() {
        val updates = Updates()
        execute(updates, Sender(), "set", "pnlibrary", "channel", "BETA")
        assertEquals("pnlibrary" to UpdateChannel.BETA, updates.channelSet)
        assertEquals(UpdateActor.Kind.CONSOLE, updates.actor?.kind)
        execute(updates, Sender(), "set", "pnlibrary", "auto", "on")
        assertEquals("pnlibrary" to true, updates.automaticSet)
        execute(updates, Sender(), "set", "pnlibrary", "hold", "3d")
        assertEquals("pnlibrary" to Duration.ofDays(3), updates.heldSet)
        execute(updates, Sender(), "set", "pnlibrary", "hold", "off")
        assertEquals("pnlibrary" to null, updates.heldSet)
    }

    @Test
    fun `invalid values and non console callers never change settings`() {
        val updates = Updates()
        execute(updates, Sender(), "set", "pnlibrary", "channel", "nightly")
        execute(updates, Sender(), "set", "pnlibrary", "auto", "maybe")
        execute(updates, Sender(), "set", "pnlibrary", "hold", "90d")
        execute(updates, Sender(isConsole = false), "set", "pnlibrary", "channel", "beta")
        assertNull(updates.channelSet)
        assertNull(updates.automaticSet)
        assertNull(updates.heldSet)
    }

    @Test
    fun `invalid plan ID never falls back to staging the current plan`() {
        val updates = Updates()
        execute(updates, Sender(), "stage", "invalid")
        assertNull(updates.staged)
    }

    @Test
    fun `rollback requires confirmation and passes the exact saved transaction`() {
        val updates = Updates()
        val id = UUID.randomUUID()
        execute(updates, Sender(), "rollback", id.toString())
        assertNull(updates.rolledBack)
        execute(updates, Sender(isConsole = false), "rollback", id.toString(), "confirm")
        assertNull(updates.rolledBack)
        execute(updates, Sender(), "rollback", id.toString(), "confirm")
        assertEquals(id, updates.rolledBack)
        assertEquals(false, updates.restoreConfigurations)
    }

    @Test
    fun `configuration restore is a separately confirmed explicit choice`() {
        val updates = Updates()
        val id = UUID.randomUUID()
        execute(updates, Sender(), "rollback", id.toString(), "configs")
        assertNull(updates.rolledBack)
        execute(updates, Sender(), "rollback", id.toString(), "configs", "confirm")
        assertEquals(id, updates.rolledBack)
        assertEquals(true, updates.restoreConfigurations)
    }

    private fun execute(updates: Updates, sender: Sender, vararg arguments: String) {
        val library = Proxy.newProxyInstance(PnLibrary::class.java.classLoader, arrayOf(PnLibrary::class.java)) { _, method, _ ->
            when (method.name) { "getUpdates" -> updates; "isClosed" -> false; else -> error("Unexpected call: ${method.name}") }
        } as PnLibrary
        val definition = ProxyUpdateCommand(library).definition()
        val route = CommandTreeRouter().route(definition, CommandContext(sender, arguments.toList(), "pnupdate", arguments.joinToString(" "))) as CommandRoute.Executable
        route.node.execution.execute(route.context).toCompletableFuture().join()
    }

    private class Sender(override val isConsole: Boolean = true) : CommandSender {
        override val id = "console"
        override val name = "Console"
        override fun hasPermission(permission: String) = true
        override fun send(message: Component) = Unit
    }

    private class Updates : UpdateService {
        val current = UpdatePlanSnapshot(UUID.randomUUID(), 1, UpdateState.UPDATE_AVAILABLE, null, emptyList(), null)
        var staged: UUID? = null
        var rolledBack: UUID? = null
        var restoreConfigurations: Boolean? = null
        var actor: UpdateActor? = null
        val pages = mutableListOf<Int>()
        var channelSet: Pair<String, UpdateChannel>? = null
        var automaticSet: Pair<String, Boolean>? = null
        var heldSet: Pair<String, Duration?>? = null
        private fun snapshot() = UpdateSnapshot("pnlibrary", "1.0.0", null, UpdateChannel.BETA, UpdateState.UP_TO_DATE, 17, 8, false, null, null)
        override fun setChannel(product: String, channel: UpdateChannel, actor: UpdateActor) =
            CompletableFuture.completedFuture(snapshot()).also { channelSet = product to channel; this.actor = actor }
        override fun setAutomaticDownload(product: String, enabled: Boolean, actor: UpdateActor) =
            CompletableFuture.completedFuture(snapshot()).also { automaticSet = product to enabled }
        override fun setHeld(product: String, duration: Duration?, actor: UpdateActor) =
            CompletableFuture.completedFuture(snapshot()).also { heldSet = product to duration }
        override fun register(owner: Any, product: ProductDescriptor, request: PluginUpdateRequest, dependencies: List<PluginDependency>): UpdateRegistration = error("not used")
        override fun all(): List<UpdateRegistration> = emptyList()
        override fun get(product: String): UpdateRegistration? = null
        override fun currentPlan(): Optional<UpdatePlanSnapshot> = Optional.of(current)
        override fun stage(planId: UUID, actor: UpdateActor) = CompletableFuture.completedFuture(current).also {
            staged = planId
            this.actor = actor
        }
        override fun historyPage(page: Int, product: String?) = CompletableFuture.completedFuture<List<UpdateHistoryEntry>>(emptyList()).also { pages.add(page) }
        override fun historyCount(product: String?) = CompletableFuture.completedFuture(0)
        override fun rollback(transactionId: UUID, actor: UpdateActor) = CompletableFuture.completedFuture(current).also { rolledBack = transactionId }
        override fun rollback(transactionId: UUID, restoreConfigurations: Boolean, actor: UpdateActor) = CompletableFuture.completedFuture(current).also {
            rolledBack = transactionId
            this.restoreConfigurations = restoreConfigurations
        }
    }
}
