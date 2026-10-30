package org.pnfolder.pnlibrary.core.commands

import net.kyori.adventure.text.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.CommandNodeKind
import org.pnfolder.pnlibrary.api.commands.CommandSender
import org.pnfolder.pnlibrary.api.runtime.PnLibrary

class ProxyControlCommandTest {
    private val definition = ProxyControlCommand(mock(PnLibrary::class.java)).definition()

    @Test
    fun `the proxy has one pn command with other labels for the case that pn is taken`() {
        assertEquals("pn", definition.name)
        assertEquals(setOf("pnlib", "pnlibrary"), definition.aliases)
    }

    @Test
    fun `the proxy has the same words as Bukkit`() {
        val words = definition.root.children.map { it.name }
        assertTrue(words.containsAll(listOf("status", "check", "updates", "update", "support", "bus", "tasks")), words.toString())
    }

    @Test
    fun `the update controls keep the catalog words under updates`() {
        val updates = definition.root.children.single { it.name == "updates" }

        assertEquals(CommandNodeKind.LITERAL, updates.kind)
        val words = updates.children.map { it.name }
        assertTrue(words.containsAll(listOf("list", "available", "history", "rollback")), words.toString())
        val update = definition.root.children.single { it.name == "update" }
        assertTrue(update.children.map { it.name }.containsAll(listOf("confirm", "rollback")))
    }

    @Test
    fun `pn alone tells a sender without the admin permission what it may use`() {
        val sent = mutableListOf<String>()
        val sender = object : CommandSender {
            override val id = "player"
            override val name = "Player"
            override val isConsole = false
            override fun hasPermission(permission: String) = permission == "pnlibrary.tasks"
            override fun send(message: Component) {
                sent += message.toString()
            }
        }

        val route = CommandTreeRouter().route(definition, CommandContext(sender, emptyList(), "pn", "")) as CommandRoute.Executable
        route.node.execution.execute(route.context).toCompletableFuture().join()

        assertTrue(sent.joinToString().contains("/pn tasks"), sent.toString())
        assertTrue(!sent.joinToString().contains("/pn debug"), sent.toString())
    }
}
