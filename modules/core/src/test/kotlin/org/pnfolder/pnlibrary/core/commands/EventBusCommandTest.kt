package org.pnfolder.pnlibrary.core.commands

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.CommandSender
import org.pnfolder.pnlibrary.api.events.Event
import org.pnfolder.pnlibrary.api.events.EventPriority
import org.pnfolder.pnlibrary.api.plugin.PluginId
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.core.events.EventHistory
import org.pnfolder.pnlibrary.core.events.EventServiceImpl
import java.util.function.Consumer

class EventBusCommandTest {
    private class Ping : Event()

    private val history = EventHistory(null, 30)
    private val events = EventServiceImpl(history) { _, _, _ -> }
    private val library = mock(PnLibrary::class.java).also { `when`(it.events).thenReturn(events) }
    private val definition = EventBusCommand(library).definition()

    @AfterEach
    fun close() = events.close()

    private fun run(vararg args: String): List<String> {
        val sent = mutableListOf<Component>()
        val sender = object : CommandSender {
            override val id = "console"
            override val name = "Console"
            override val isConsole = true
            override fun hasPermission(permission: String) = true
            override fun send(message: Component) { sent += message }
        }
        val route = CommandTreeRouter().route(definition, CommandContext(sender, args.toList(), "bus", args.joinToString(" ")))
            as CommandRoute.Executable
        route.node.execution.execute(route.context).toCompletableFuture().join()
        return sent.map(PlainTextComponentSerializer.plainText()::serialize)
    }

    private fun listen(plugin: String, action: () -> Unit) {
        events.scope(PluginId.of(plugin)).subscribe(Ping::class.java, EventPriority.NORMAL, Consumer { action() })
    }

    @Test
    fun `slow names a listener that ran and says so when none did`() {
        assertTrue(run("slow").any { it.contains("ещё не запускались") })
        listen("shop") { Thread.sleep(2) }
        events.callEvent(Ping())
        val text = run("slow").joinToString("\n")
        assertTrue(text.contains("shop") && text.contains("Ping"), text)
    }

    @Test
    fun `errors lists only listeners that threw`() {
        listen("good") {}
        listen("bad") { error("boom") }
        events.callEvent(Ping())
        val text = run("errors").joinToString("\n")
        assertTrue(text.contains("bad") && text.contains("ошибок 1"), text)
        assertTrue(!text.contains("good"), text)
    }

    @Test
    fun `plugin view filters by plugin and top ranks events by calls`() {
        listen("shop") {}
        listen("other") {}
        repeat(3) { events.callEvent(Ping()) }
        val plugin = run("plugin", "shop").joinToString("\n")
        assertTrue(plugin.contains("shop") && !plugin.contains("other"), plugin)
        assertTrue(run("top").joinToString("\n").contains("3"), "top")
    }

    @Test
    fun `range counts calls from the hourly history`() {
        events.callEvent(Ping())
        events.callEvent(Ping())
        val text = run("range", "1h").joinToString("\n")
        assertTrue(text.contains("Ping") && text.contains("2"), text)
    }
}
