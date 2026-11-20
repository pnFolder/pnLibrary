package org.pnfolder.pnlibrary.core.commands.proxy

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.CommandSender
import org.pnfolder.pnlibrary.core.commands.engine.CommandRoute
import org.pnfolder.pnlibrary.core.commands.engine.CommandTreeRouter
import org.pnfolder.pnlibrary.core.link.Link
import org.pnfolder.pnlibrary.core.link.report.LinkPlugin
import org.pnfolder.pnlibrary.core.link.report.LinkReport
import org.pnfolder.pnlibrary.core.link.report.NetworkServer
import java.time.Instant

class ServersCommandTest {
    private fun report(players: Int, vararg plugins: LinkPlugin) = LinkReport("Paper 1.21", "0.1.0", players, plugins.toList(), Instant.now())

    private val servers = listOf(
        NetworkServer("lobby", true, report(3, LinkPlugin("shop", "1.0", "1.1", "UPDATE_AVAILABLE"))),
        NetworkServer("games", false, report(0)),
    )
    private val sent = mutableListOf<String>()
    private val sender = object : CommandSender {
        override val id = "console"
        override val name = "Console"
        override val isConsole = true
        override fun hasPermission(permission: String) = true
        override fun send(message: Component) {
            sent += PlainTextComponentSerializer.plainText().serialize(message)
        }
    }

    private fun run(shown: List<NetworkServer>, link: Link = Link.OFF, vararg words: String): String {
        sent.clear()
        val definition = ServersCommand(link) { shown }.definition()
        val route = CommandTreeRouter().route(definition, CommandContext(sender, words.toList(), "servers", words.joinToString(" ")))
        (route as CommandRoute.Executable).node.execution.execute(route.context).toCompletableFuture().join()
        return sent.joinToString("\n")
    }

    @Test
    fun `the list shows each server with its state, players and the number of updates`() {
        val text = run(servers)

        assertTrue(text.contains("lobby"), text)
        assertTrue(text.contains("на связи"), text)
        assertTrue(text.contains("games"), text)
        assertTrue(text.contains("нет связи"), text)
        assertTrue(text.contains("3 игрока"), text)
        assertTrue(text.contains("обновлений: 1"), text)
    }

    @Test
    fun `an empty list explains what to do about the key`() {
        assertTrue(run(emptyList()).contains("link.key"))
    }

    @Test
    fun `one server shows its plugins with versions and the update state`() {
        val text = run(servers, Link.OFF, "lobby")

        assertTrue(text.contains("shop"), text)
        assertTrue(text.contains("v1.0 >> v1.1"), text)
    }

    @Test
    fun `the name is matched without regard to case and an unknown server is refused`() {
        assertTrue(run(servers, Link.OFF, "LOBBY").contains("pnLibrary"))
        assertTrue(run(servers, Link.OFF, "ghost").contains("не присылал отчёт"))
    }

    @Test
    fun `a request without a link key says why it was not sent`() {
        val text = run(servers, Link.OFF, "lobby", "check")

        assertTrue(text.contains("нет файла link.key"), text)
    }

    @Test
    fun `the permission is the same for the list and its words`() {
        val definition = ServersCommand(Link.OFF) { servers }.definition()

        assertEquals("servers", definition.name)
        assertTrue(definition.root.children.single().children.map { it.name }.containsAll(listOf("check", "update", "auto")))
    }
}
