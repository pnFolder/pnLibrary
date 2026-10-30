package org.pnfolder.pnlibrary.core.updates.catalog

import net.kyori.adventure.text.Component
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.CommandSender
import org.pnfolder.pnlibrary.api.commands.command
import org.pnfolder.pnlibrary.api.dependencies.PluginDependency
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.api.updates.*
import org.pnfolder.pnlibrary.api.updates.backup.*
import org.pnfolder.pnlibrary.core.commands.CommandRoute
import org.pnfolder.pnlibrary.core.commands.CommandTreeRouter
import java.lang.reflect.Proxy
import java.time.Duration
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletableFuture

class ProxyUpdateCommandTest {
    @Test
    fun `a player with the permission may change settings and is recorded as a player`() {
        val updates = Updates()
        execute(updates, Sender(), "updates", "set", "pnlibrary", "channel", "DEV")
        assertEquals("pnlibrary" to UpdateChannel.DEV, updates.channelSet)
        assertEquals(UpdateActor.Kind.PLAYER, updates.actor?.kind)
        execute(updates, Sender(console = true), "updates", "set", "pnlibrary", "auto", "on")
        assertEquals("pnlibrary" to true, updates.automaticSet)
        execute(updates, Sender(), "updates", "set", "pnlibrary", "hold", "3d")
        assertEquals("pnlibrary" to Duration.ofDays(3), updates.heldSet)
    }

    @Test
    fun `a sender without the permission cannot reach a command`() {
        val updates = Updates()
        assertNull(route(updates, Sender(allowed = emptySet()), "updates", "set", "pnlibrary", "auto", "on") as? CommandRoute.Executable)
        assertNull(route(updates, Sender(allowed = emptySet()), "update", "rollback") as? CommandRoute.Executable)
    }

    @Test
    fun `invalid values never change settings`() {
        val updates = Updates()
        execute(updates, Sender(), "updates", "set", "pnlibrary", "channel", "nightly")
        execute(updates, Sender(), "updates", "set", "pnlibrary", "auto", "maybe")
        execute(updates, Sender(), "updates", "set", "pnlibrary", "hold", "90d")
        assertNull(updates.channelSet)
        assertNull(updates.automaticSet)
        assertNull(updates.heldSet)
    }

    @Test
    fun `a saved rollback asks first and runs only on the token of the same sender`() {
        val updates = Updates()
        val id = UUID.randomUUID()
        val asker = Sender()
        execute(updates, asker, "updates", "rollback", id.toString())
        assertNull(updates.rolledBack)
        val token = asker.token()
        execute(updates, Sender(id = "other"), "update", "confirm", token)
        assertNull(updates.rolledBack)
        execute(updates, asker, "update", "confirm", token)
        assertEquals(id, updates.rolledBack)
        assertEquals(false, updates.restoreConfigurations)
        updates.rolledBack = null
        execute(updates, asker, "update", "confirm", token)
        assertNull(updates.rolledBack)
    }

    @Test
    fun `configuration restore is a separately confirmed explicit choice`() {
        val updates = Updates()
        val id = UUID.randomUUID()
        val asker = Sender()
        execute(updates, asker, "updates", "rollback", id.toString(), "configs")
        assertNull(updates.rolledBack)
        execute(updates, asker, "update", "confirm", asker.token())
        assertEquals(id, updates.rolledBack)
        assertEquals(true, updates.restoreConfigurations)
    }

    /** One command per update service, so that the tokens it issued are still known to the next call. */
    private val definitions = mutableMapOf<Updates, CommandDefinition>()

    private fun route(updates: Updates, sender: Sender, vararg arguments: String): CommandRoute {
        val definition = definitions.getOrPut(updates) {
            val library = Proxy.newProxyInstance(PnLibrary::class.java.classLoader, arrayOf(PnLibrary::class.java)) { _, method, _ ->
                when (method.name) { "getUpdates" -> updates; "isClosed" -> false; else -> error("Unexpected call: ${method.name}") }
            } as PnLibrary
            val controls = ProxyUpdateCommand(library)
            command("pn") { controls.install(this) }
        }
        return CommandTreeRouter().route(definition, CommandContext(sender, arguments.toList(), "pn", arguments.joinToString(" ")))
    }

    private fun execute(updates: Updates, sender: Sender, vararg arguments: String) {
        val route = route(updates, sender, *arguments) as CommandRoute.Executable
        route.node.execution.execute(route.context).toCompletableFuture().join()
    }

    private class Sender(
        override val id: String = "player",
        console: Boolean = false,
        private val allowed: Set<String>? = null,
    ) : CommandSender {
        override val isConsole = console
        override val name = id
        private val sent = mutableListOf<Component>()
        override fun hasPermission(permission: String) = allowed?.contains(permission) ?: true
        override fun send(message: Component) { sent += message }

        /** The token in the confirmation button the sender was shown. */
        fun token(): String = Regex("update confirm ([A-Za-z0-9_-]+)").find(sent.joinToString { it.toString() })!!.groupValues[1]
    }

    private class Updates : UpdateService {
        val current = UpdatePlanSnapshot(UUID.randomUUID(), 1, UpdateState.UPDATE_AVAILABLE, null, emptyList(), null)
        var staged: UUID? = null
        var rolledBack: UUID? = null
        var restoreConfigurations: Boolean? = null
        var actor: UpdateActor? = null
        val pages = mutableListOf<Int>()
        var channelSet: Pair<String, UpdateChannel?>? = null
        var automaticSet: Pair<String, Boolean>? = null
        var heldSet: Pair<String, Duration?>? = null
        private fun snapshot() = UpdateSnapshot("pnlibrary", "1.0.0", null, UpdateChannel.BETA, UpdateState.UP_TO_DATE, 17, 8, false, null, null)
        override fun setChannel(product: String, channel: UpdateChannel?, actor: UpdateActor) =
            CompletableFuture.completedFuture(snapshot()).also { channelSet = product to channel; this.actor = actor }
        override fun setAutomaticDownload(product: String, enabled: Boolean, actor: UpdateActor) =
            CompletableFuture.completedFuture(snapshot()).also { automaticSet = product to enabled }
        override fun setHeld(product: String, duration: Duration?, actor: UpdateActor) =
            CompletableFuture.completedFuture(snapshot()).also { heldSet = product to duration }
        override fun register(owner: Any, product: ProductDescriptor, request: PluginUpdateRequest,
            dependencies: List<PluginDependency>): UpdateRegistration = error("not used")
        override fun all(): List<UpdateRegistration> = emptyList()
        override fun get(product: String): UpdateRegistration? = null
        override fun currentPlan(): Optional<UpdatePlanSnapshot> = Optional.of(current)
        override fun stage(planId: UUID, actor: UpdateActor) = CompletableFuture.completedFuture(current).also {
            staged = planId
            this.actor = actor
        }
        override fun historyPage(page: Int,
            product: String?) = CompletableFuture.completedFuture<List<UpdateHistoryEntry>>(emptyList()).also { pages.add(page) }
        override fun historyCount(product: String?) = CompletableFuture.completedFuture(0)
        override fun rollback(transactionId: UUID, actor: UpdateActor) = CompletableFuture.completedFuture(current).also { rolledBack = transactionId }
        override fun rollback(transactionId: UUID, restoreConfigurations: Boolean, actor: UpdateActor) = CompletableFuture.completedFuture(current).also {
            rolledBack = transactionId
            this.restoreConfigurations = restoreConfigurations
        }
    }
}
