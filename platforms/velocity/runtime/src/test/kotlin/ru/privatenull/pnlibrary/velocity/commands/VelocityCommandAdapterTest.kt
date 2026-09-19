package ru.privatenull.pnlibrary.velocity.commands

import com.velocitypowered.api.command.CommandSource
import net.kyori.adventure.text.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.commands.CommandContext
import ru.privatenull.pnlibrary.api.commands.CommandDefinition
import ru.privatenull.pnlibrary.api.commands.command
import ru.privatenull.pnlibrary.spi.commands.PlatformCommandDispatcher
import ru.privatenull.pnlibrary.spi.commands.PlatformCommandRegistration
import java.lang.reflect.Proxy
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

class VelocityCommandAdapterTest {
    @Test
    fun `execution and asynchronous suggestions are transported without blocking`() {
        val registrar = RecordingRegistrar()
        val sent = mutableListOf<Component>()
        val source = source("VelocityConsole", setOf("example.use"), sent)
        val adapter = VelocityCommandAdapter(
            plugin = Any(),
            registrar = registrar,
            isConsole = { it === source },
        )
        val pending = CompletableFuture<List<String>>()
        val dispatcher = RecordingDispatcher(pending)
        adapter.register(Any(), command("hello") { aliases("hi") }, dispatcher)

        registrar.execute(source, arrayOf("one"))
        val returned = registrar.suggest(source, arrayOf("w"))

        assertFalse(returned.toCompletableFuture().isDone)
        pending.complete(listOf("world"))
        assertEquals(listOf("world"), returned.toCompletableFuture().join())
        assertEquals(listOf("one"), dispatcher.execution.arguments)
        assertEquals("w", dispatcher.suggestion.currentInput)
        assertTrue(dispatcher.execution.sender.isConsole)
        assertTrue(dispatcher.execution.sender.hasPermission("example.use"))
        dispatcher.execution.sender.send(Component.text("done"))
        assertEquals(listOf(Component.text("done")), sent)
    }

    @Test
    fun `adapter closes every native registration exactly once`() {
        val registrar = RecordingRegistrar()
        val adapter = VelocityCommandAdapter(Any(), registrar) { false }
        val first = adapter.register(Any(), command("first") {}, RecordingDispatcher())
        adapter.register(Any(), command("second") {}, RecordingDispatcher())

        first.close()
        first.close()
        adapter.close()

        assertEquals(2, registrar.unregisterCalls)
    }

    private class RecordingDispatcher(
        private val suggestions: CompletableFuture<List<String>> =
            CompletableFuture.completedFuture(emptyList()),
    ) : PlatformCommandDispatcher {
        lateinit var execution: CommandContext
        lateinit var suggestion: CommandContext
        override fun execute(command: CommandDefinition, context: CommandContext) =
            CompletableFuture.completedFuture<Void>(null).also { execution = context }
        override fun suggest(command: CommandDefinition, context: CommandContext): CompletionStage<List<String>> =
            suggestions.also { suggestion = context }
    }

    private class RecordingRegistrar : VelocityNativeCommandRegistrar {
        lateinit var execute: (CommandSource, Array<String>) -> Unit
        lateinit var suggest: (CommandSource, Array<String>) -> CompletionStage<List<String>>
        var unregisterCalls = 0
        override fun register(
            command: CommandDefinition,
            execute: (CommandSource, Array<String>) -> Unit,
            suggest: (CommandSource, Array<String>) -> CompletionStage<List<String>>,
        ): PlatformCommandRegistration {
            this.execute = execute
            this.suggest = suggest
            return PlatformCommandRegistration { unregisterCalls++ }
        }
    }

    private fun source(
        name: String,
        permissions: Set<String>,
        messages: MutableList<Component>,
    ): CommandSource = CommandSource::class.java.cast(
        Proxy.newProxyInstance(CommandSource::class.java.classLoader, arrayOf(CommandSource::class.java)) { _, method, args ->
            when (method.name) {
                "toString" -> name
                "hasPermission" -> args?.firstOrNull() in permissions
                "sendMessage" -> {
                    messages += args?.first() as Component
                    Unit
                }
                else -> when (method.returnType) {
                    java.lang.Boolean.TYPE -> false
                    java.lang.Integer.TYPE -> 0
                    java.lang.Long.TYPE -> 0L
                    java.lang.Void.TYPE -> Unit
                    else -> null
                }
            }
        },
    )
}
