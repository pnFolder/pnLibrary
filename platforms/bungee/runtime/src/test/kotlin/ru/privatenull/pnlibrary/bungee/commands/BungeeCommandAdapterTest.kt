package ru.privatenull.pnlibrary.bungee.commands

import net.kyori.adventure.text.Component
import net.kyori.adventure.sound.Sound
import net.md_5.bungee.api.CommandSender
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.commands.CommandContext
import ru.privatenull.pnlibrary.api.audiences.AudienceSender
import ru.privatenull.pnlibrary.api.commands.CommandDefinition
import ru.privatenull.pnlibrary.api.commands.command
import ru.privatenull.pnlibrary.spi.commands.PlatformCommandDispatcher
import ru.privatenull.pnlibrary.spi.commands.PlatformCommandRegistration
import java.lang.reflect.Proxy
import java.util.concurrent.CompletableFuture

class BungeeCommandAdapterTest {
    @Test
    fun `native callbacks translate sender execution and suggestions`() {
        val registrar = RecordingRegistrar()
        val messages = mutableListOf<Component>()
        val actionBars = mutableListOf<Component>()
        val nativeSender = sender("ProxyConsole", setOf("example.use"))
        val adapter = BungeeCommandAdapter(
            plugin = Any(),
            registrar = registrar,
            audience = { audience(it, it === nativeSender, messages, actionBars) },
        )
        val dispatcher = RecordingDispatcher()
        adapter.register(Any(), command("hello") { aliases("hi") }, dispatcher)

        registrar.execute(nativeSender, arrayOf("one"))
        val suggestions = registrar.suggest(nativeSender, arrayOf("w"))
        dispatcher.execution.sender.send(Component.text("done"))
        dispatcher.execution.sender.actionBar(Component.text("status"))

        assertEquals("hello", dispatcher.execution.invokedAlias)
        assertEquals(listOf("one"), dispatcher.execution.arguments)
        assertEquals("w", dispatcher.suggestion.currentInput)
        assertEquals(listOf("world"), suggestions)
        assertTrue(dispatcher.execution.sender.isConsole)
        assertTrue(dispatcher.execution.sender.hasPermission("example.use"))
        assertEquals(listOf(Component.text("done")), messages)
        assertEquals(listOf(Component.text("status")), actionBars)
    }

    @Test
    fun `closing registration and adapter unregisters each native command once`() {
        val registrar = RecordingRegistrar()
        val adapter = BungeeCommandAdapter(
            plugin = Any(),
            registrar = registrar,
            audience = { audience(it, false, mutableListOf(), mutableListOf()) },
        )
        val first = adapter.register(Any(), command("first") {}, RecordingDispatcher())
        adapter.register(Any(), command("second") {}, RecordingDispatcher())

        first.close()
        first.close()
        adapter.close()

        assertEquals(2, registrar.unregisterCalls)
    }

    private class RecordingDispatcher : PlatformCommandDispatcher {
        lateinit var execution: CommandContext
        lateinit var suggestion: CommandContext
        override fun execute(command: CommandDefinition, context: CommandContext) =
            CompletableFuture.completedFuture<Void>(null).also { execution = context }
        override fun suggest(command: CommandDefinition, context: CommandContext) =
            CompletableFuture.completedFuture(listOf("world")).also { suggestion = context }
    }

    private class RecordingRegistrar : BungeeNativeCommandRegistrar {
        lateinit var execute: (CommandSender, Array<String>) -> Unit
        lateinit var suggest: (CommandSender, Array<String>) -> List<String>
        var unregisterCalls = 0
        override fun register(
            command: CommandDefinition,
            execute: (CommandSender, Array<String>) -> Unit,
            suggest: (CommandSender, Array<String>) -> List<String>,
        ): PlatformCommandRegistration {
            this.execute = execute
            this.suggest = suggest
            return PlatformCommandRegistration { unregisterCalls++ }
        }
    }

    private fun sender(name: String, permissions: Set<String>): CommandSender =
        proxy(CommandSender::class.java) { method, args ->
            when (method.name) {
                "getName" -> name
                "hasPermission" -> args?.firstOrNull() in permissions
                else -> defaultValue(method.returnType)
            }
        }

    private fun audience(
        sender: CommandSender,
        console: Boolean,
        messages: MutableList<Component>,
        actionBars: MutableList<Component>,
    ) = object : AudienceSender {
        override val id get() = sender.name
        override val name get() = sender.name
        override val isConsole = console
        override fun hasPermission(permission: String) = sender.hasPermission(permission)
        override fun sendMessage(text: Component) { messages += text }
        override fun actionBar(text: Component) { actionBars += text }
        override fun playSound(sound: Sound) = false
    }

    private fun <T> proxy(
        type: Class<T>,
        handler: (java.lang.reflect.Method, Array<out Any?>?) -> Any? = { method, _ -> defaultValue(method.returnType) },
    ): T = type.cast(Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args -> handler(method, args) })

    private fun defaultValue(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE -> false
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Void.TYPE -> Unit
        else -> null
    }
}
