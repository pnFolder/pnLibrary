package ru.privatenull.pnlibrary.bukkit.commands

import net.kyori.adventure.text.Component
import org.bukkit.command.CommandSender
import org.bukkit.plugin.Plugin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.commands.CommandContext
import ru.privatenull.pnlibrary.api.commands.CommandDefinition
import ru.privatenull.pnlibrary.api.commands.command
import ru.privatenull.pnlibrary.spi.commands.PlatformCommandDispatcher
import ru.privatenull.pnlibrary.spi.commands.PlatformCommandRegistration
import java.lang.reflect.Proxy
import java.util.concurrent.CompletableFuture

class BukkitCommandAdapterTest {
    @Test
    fun `native callbacks forward execution alias arguments and suggestions`() {
        val registrar = RecordingRegistrar()
        val sent = mutableListOf<Component>()
        val adapter = BukkitCommandAdapter(
            plugin = proxy(Plugin::class.java) { defaultValue(it.returnType) },
            registrar = registrar,
            sendMessage = { _, component -> sent += component },
        )
        val definition = command("hello") { aliases("hi") }
        val dispatcher = RecordingDispatcher()
        adapter.register(Any(), definition, dispatcher)
        val sender = sender("Alice", permissions = setOf("example.use"))

        registrar.execute(sender, "HI", arrayOf("one", "two"))
        val suggestions = registrar.suggest(sender, "hello", arrayOf("wo"))
        dispatcher.lastContext.sender.send(Component.text("done"))

        assertEquals("HI", dispatcher.executionContext.invokedAlias)
        assertEquals(listOf("one", "two"), dispatcher.executionContext.arguments)
        assertEquals("wo", dispatcher.lastContext.currentInput)
        assertEquals(listOf("world"), suggestions)
        assertEquals(listOf(Component.text("done")), sent)
        assertEquals("Alice", dispatcher.executionContext.sender.name)
        assertEquals(true, dispatcher.executionContext.sender.hasPermission("example.use"))
    }

    @Test
    fun `registration and adapter close unregister each native command once`() {
        val registrar = RecordingRegistrar()
        val adapter = BukkitCommandAdapter(
            plugin = proxy(Plugin::class.java) { defaultValue(it.returnType) },
            registrar = registrar,
            sendMessage = { _, _ -> },
        )
        val first = adapter.register(Any(), command("first") {}, RecordingDispatcher())
        adapter.register(Any(), command("second") {}, RecordingDispatcher())

        first.close()
        first.close()
        adapter.close()
        adapter.close()

        assertEquals(2, registrar.unregisterCalls)
    }

    private class RecordingDispatcher : PlatformCommandDispatcher {
        lateinit var executionContext: CommandContext
        lateinit var lastContext: CommandContext
        override fun execute(command: CommandDefinition, context: CommandContext) =
            CompletableFuture.completedFuture<Void>(null).also { executionContext = context }
        override fun suggest(command: CommandDefinition, context: CommandContext) =
            CompletableFuture.completedFuture(listOf("world")).also { lastContext = context }
    }

    private class RecordingRegistrar : BukkitNativeCommandRegistrar {
        lateinit var execute: (CommandSender, String, Array<String>) -> Unit
        lateinit var suggest: (CommandSender, String, Array<String>) -> List<String>
        var unregisterCalls = 0

        override fun register(
            command: CommandDefinition,
            execute: (CommandSender, String, Array<String>) -> Unit,
            suggest: (CommandSender, String, Array<String>) -> List<String>,
        ): PlatformCommandRegistration {
            this.execute = execute
            this.suggest = suggest
            return PlatformCommandRegistration { unregisterCalls++ }
        }
    }

    private fun sender(name: String, permissions: Set<String>): CommandSender =
        proxy(CommandSender::class.java) { method ->
            when (method.name) {
                "getName" -> name
                "hasPermission" -> method.lastArguments?.firstOrNull() in permissions
                else -> defaultValue(method.returnType)
            }
        }

    private fun <T> proxy(type: Class<T>, handler: (MethodCall) -> Any?): T = type.cast(
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
            handler(MethodCall(method.name, method.returnType, args))
        },
    )

    private data class MethodCall(
        val name: String,
        val returnType: Class<*>,
        val lastArguments: Array<out Any?>?,
    )

    private fun defaultValue(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE -> false
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Void.TYPE -> Unit
        else -> null
    }
}
