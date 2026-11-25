package org.pnfolder.pnlibrary.core.commands.registry

import org.pnfolder.pnlibrary.core.registry.ListRegistry
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.audience.senderPointers
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.CommandSender
import org.pnfolder.pnlibrary.api.registry.RegistryEntry
import org.pnfolder.pnlibrary.api.registry.Visibility
import org.pnfolder.pnlibrary.core.commands.engine.CommandRoute
import org.pnfolder.pnlibrary.core.commands.engine.CommandTreeRouter

class RegistryCommandTest {
    private val sent = mutableListOf<String>()
    private val sender = object : CommandSender {
        override fun pointers() = senderPointers("Console") { true }
        override fun sendMessage(message: Component) {
            sent += PlainTextComponentSerializer.plainText().serialize(message)
        }
    }
    private val definition = registryCommand(
        listOf(
            ListRegistry("placeholders") { listOf(RegistryEntry("shop.coins", "x", "shop/main", Visibility.PLUGIN, "Монеты")) },
            ListRegistry("services") { listOf(RegistryEntry("java.lang.Runnable", "y", "pnLibrary")) },
        ),
    )

    private fun run(vararg words: String): String {
        sent.clear()
        val route = CommandTreeRouter().route(definition, CommandContext(sender, words.toList(), "registry", words.joinToString(" ")))
        (route as CommandRoute.Executable).node.execution.execute(route.context).toCompletableFuture().join()
        return sent.joinToString("\n")
    }

    @Test
    fun `without words it lists the kinds with their sizes`() {
        val text = run()

        assertTrue("placeholders  1" in text && "services  1" in text, text)
    }

    @Test
    fun `a kind shows owner, visibility and description of each entry`() {
        val text = run("placeholders")

        assertTrue("shop.coins  shop/main  [plugin]  Монеты" in text, text)
    }

    @Test
    fun `find searches every kind by key or owner`() {
        val text = run("find", "pnlibrary")

        assertTrue("services  java.lang.Runnable" in text, text)
    }

    @Test
    fun `an unknown kind names the ones that exist`() {
        assertTrue("placeholders, services" in run("nothing"))
    }
}
