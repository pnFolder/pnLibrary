package ru.privatenull.pnlibrary.core.diagnostics

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.commands.CommandContext
import ru.privatenull.pnlibrary.api.commands.CommandSender
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.api.plugin.PluginContext
import ru.privatenull.pnlibrary.api.plugin.ModuleContext
import ru.privatenull.pnlibrary.api.plugin.ModuleId
import ru.privatenull.pnlibrary.api.plugin.PluginMetadata
import ru.privatenull.pnlibrary.api.plugin.PluginRegistry
import ru.privatenull.pnlibrary.api.plugin.PluginId
import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import java.lang.reflect.Proxy

class DiagnosticCommandTest {
    @Test
    fun `definition contains all portable metadata and filtered suggestions`() {
        val library = libraryWithPlugins(listOf(metadata("PnClans")))
        val definition = diagnosticCommand(library)

        val suggestions = definition.suggestions.suggest(
            CommandContext(TestSender(), emptyList(), "pndebug", "example"),
        ).toCompletableFuture().join()

        assertEquals("pndebug", definition.name)
        assertEquals(setOf("pnlib"), definition.aliases)
        assertEquals("pnlibrary.debug", definition.permission)
        assertTrue(definition.consoleBypassesPermission)
        assertEquals(listOf("example.pnclans"), suggestions)
    }

    @Test
    fun `suggestions expose target and flags from one shared definition`() {
        val library = libraryWithPlugins(emptyList())
        val definition = diagnosticCommand(library)

        val suggestions = definition.suggestions.suggest(
            CommandContext(TestSender(), emptyList(), "pndebug", ""),
        ).toCompletableFuture().join()

        assertEquals(
            listOf("all", "--full", "--config", "--logs", "--local"),
            suggestions,
        )
    }

    @Test
    fun `invalid invocation renders usage without a platform renderer`() {
        val library = libraryWithPlugins(emptyList())
        val sender = TestSender()
        val definition = diagnosticCommand(library)

        definition.execution.execute(
            CommandContext(sender, listOf("--unknown"), "pndebug", "--unknown"),
        ).toCompletableFuture().join()

        assertEquals(
            listOf("/pndebug [all|plugin] [--full|--config|--logs] [--local]"),
            sender.messages.map(PlainTextComponentSerializer.plainText()::serialize),
        )
    }

    private fun metadata(name: String) = PluginMetadata(
        id = ModuleId.of(name),
        name = name,
        version = "1.0.0",
        authors = "pnFolder",
        platform = PlatformType.BUKKIT,
        platformImplementation = "Paper",
        javaVersion = "21",
        javaFeature = 21,
    )

    private fun libraryWithPlugins(metadata: List<PluginMetadata>): PnLibrary {
        val modules = metadata.map { value ->
            proxy(ModuleContext::class.java) { method ->
                when (method.name) {
                    "getMetadata" -> value
                    "getKey" -> PluginId.of("example.${value.id.value}")
                    else -> defaultValue(method.returnType)
                }
            }
        }
        val contexts = modules.map { module ->
            proxy(PluginContext::class.java) { method ->
                if (method.name == "modules") listOf(module) else defaultValue(method.returnType)
            }
        }
        val plugins = proxy(PluginRegistry::class.java) { method ->
            if (method.name == "registrations") contexts else defaultValue(method.returnType)
        }
        return proxy(PnLibrary::class.java) { method ->
            if (method.name == "getPlugins") plugins else defaultValue(method.returnType)
        }
    }

    private fun <T> proxy(type: Class<T>, handler: (java.lang.reflect.Method) -> Any?): T =
        type.cast(Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ -> handler(method) })

    private fun defaultValue(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE -> false
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Void.TYPE -> Unit
        else -> null
    }

    private class TestSender : CommandSender {
        val messages = mutableListOf<Component>()
        override val id: String = "console"
        override val name: String = "Console"
        override val isConsole: Boolean = true
        override fun hasPermission(permission: String): Boolean = true
        override fun send(message: Component) { messages += message }
    }
}
