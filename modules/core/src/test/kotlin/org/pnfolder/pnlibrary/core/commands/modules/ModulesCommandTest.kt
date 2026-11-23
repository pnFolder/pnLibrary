package org.pnfolder.pnlibrary.core.commands.modules

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.CommandSender
import org.pnfolder.pnlibrary.api.plugin.ModuleContext
import org.pnfolder.pnlibrary.api.plugin.control.ModuleState
import org.pnfolder.pnlibrary.api.plugin.control.PluginModule
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.core.commands.engine.CommandRoute
import org.pnfolder.pnlibrary.core.commands.engine.CommandTreeRouter
import org.pnfolder.pnlibrary.core.plugin.PluginRegistryImplTest

class ModulesCommandTest {
    private class Switchable : PluginModule {
        override fun onEnable(context: ModuleContext) {}
        override fun onDisable(context: ModuleContext) {}
    }

    private val registry = PluginRegistryImplTest.registry()
    private val library = mock(PnLibrary::class.java).also { `when`(it.plugins).thenReturn(registry) }
    private val sent = mutableListOf<String>()
    private val sender = object : CommandSender {
        override fun pointers() = org.pnfolder.pnlibrary.api.sender.senderPointers("Console") { permission -> true }
        override fun send(message: Component) {
            sent += PlainTextComponentSerializer.plainText().serialize(message)
        }
    }
    private val definition = ModulesCommand(library).definition()

    private fun run(vararg words: String): String {
        sent.clear()
        val route = CommandTreeRouter().route(definition, CommandContext(sender, words.toList(), "modules", words.joinToString(" ")))
        (route as CommandRoute.Executable).node.execution.execute(route.context).toCompletableFuture().join()
        return sent.joinToString("\n")
    }

    @Test
    fun `an empty server says there are no modules`() {
        assertTrue(run().contains("Модулей пока нет"))
        registry.close()
    }

    @Test
    fun `the list shows every module with its state and a module without hooks is marked`() {
        val plugin = registry.register(Any())
        val shop = plugin.registerModule("shop", Switchable())
        plugin.registerModule("plain")

        val text = run()

        assertTrue(text.contains(":shop"), text)
        assertTrue(text.contains("работает"), text)
        assertTrue(text.contains("без переключателя"), text)
        assertEquals(ModuleState.ENABLED, shop.control.state)
        registry.close()
    }

    @Test
    fun `disable and enable switch the module and view shows the state`() {
        val module = registry.register(Any()).registerModule("shop", Switchable())
        val name = ModuleLabels.named(library).single().first

        run("disable", name)
        assertEquals(ModuleState.DISABLED, module.control.state)
        assertTrue(run("view", name).contains("выключен"))

        run("enable", name)
        assertEquals(ModuleState.ENABLED, module.control.state)
        registry.close()
    }

    @Test
    fun `an unknown module is refused with a pointer to the list`() {
        val text = run("view", "nothing:here")

        assertTrue(text.contains("Такого модуля нет"), text)
        registry.close()
    }

    @Test
    fun `auto off keeps the module from starting with the server`() {
        val module = registry.register(Any()).registerModule("shop", Switchable())
        val name = ModuleLabels.named(library).single().first

        run("auto", name, "off")

        assertEquals(false, module.control.autoStart)
        assertTrue(run().contains("не включается сам"))
        registry.close()
    }

    @Test
    fun `plugins lists each plugin with its modules`() {
        registry.register(Any()).registerModule("shop", Switchable())

        val text = run("plugins")

        assertTrue(text.contains("модулей 1"), text)
        registry.close()
    }
}
