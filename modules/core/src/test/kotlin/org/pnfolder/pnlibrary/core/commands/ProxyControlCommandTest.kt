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
    fun `the update controls hang under update and keep their own words`() {
        val update = definition.root.children.single { it.name == "update" }

        assertEquals(CommandNodeKind.LITERAL, update.kind)
        val words = update.children.map { it.name }
        assertTrue(words.containsAll(listOf("plan", "check", "stage", "rollback", "list", "available", "history")), words.toString())
    }

    @Test
    fun `pn alone names what is there`() {
        val sent = mutableListOf<String>()
        val sender = object : CommandSender {
            override val id = "console"
            override val name = "Console"
            override val isConsole = true
            override fun hasPermission(permission: String) = true
            override fun send(message: Component) {
                sent += (message as net.kyori.adventure.text.TextComponent).content()
            }
        }

        val route = CommandTreeRouter().route(definition, CommandContext(sender, emptyList(), "pn", "")) as CommandRoute.Executable
        route.node.execution.execute(route.context).toCompletableFuture().join()

        assertEquals(listOf("Команды pnLibrary: /pn events, /pn bus, /pn debug, /pn update"), sent)
    }
}
