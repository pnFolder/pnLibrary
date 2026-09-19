package ru.privatenull.pnlibrary.core.commands

import net.kyori.adventure.text.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.commands.CommandContext
import ru.privatenull.pnlibrary.api.commands.CommandDefinition
import ru.privatenull.pnlibrary.api.commands.CommandSender
import ru.privatenull.pnlibrary.api.commands.command
import ru.privatenull.pnlibrary.api.logging.LogLevel
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.spi.commands.PlatformCommandAdapter
import ru.privatenull.pnlibrary.spi.commands.PlatformCommandDispatcher
import ru.privatenull.pnlibrary.spi.commands.PlatformCommandRegistration
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
import java.util.concurrent.CompletableFuture

class CommandServiceImplTest {
    @Test
    fun `authorized execution and suggestions reach the portable definition`() {
        val native = RecordingCommands()
        val service = CommandServiceImpl(TestPlatform(native))
        var executed = false
        service.register(Any(), command("hello") {
            permission("example.hello")
            executes { executed = true }
            suggests { listOf("world") }
        })
        val context = context(TestSender(permissions = setOf("example.hello")))

        native.dispatcher.execute(native.command, context).toCompletableFuture().join()
        val suggestions = native.dispatcher.suggest(native.command, context).toCompletableFuture().join()

        assertTrue(executed)
        assertEquals(listOf("world"), suggestions)
    }

    @Test
    fun `permission denial blocks execution and suggestions while configured console bypass works`() {
        val native = RecordingCommands()
        val service = CommandServiceImpl(TestPlatform(native))
        var executions = 0
        service.register(Any(), command("hello") {
            permission("example.hello")
            consoleBypassesPermission()
            executes { executions++ }
            suggests { listOf("secret") }
        })
        val denied = TestSender()

        native.dispatcher.execute(native.command, context(denied)).toCompletableFuture().join()
        val deniedSuggestions = native.dispatcher
            .suggest(native.command, context(denied)).toCompletableFuture().join()
        native.dispatcher.execute(
            native.command,
            context(TestSender(console = true)),
        ).toCompletableFuture().join()

        assertEquals(1, executions)
        assertTrue(deniedSuggestions.isEmpty())
        assertEquals(listOf(Component.text("You do not have permission.")), denied.messages)
    }

    @Test
    fun `name and alias collisions fail before native registration`() {
        val native = RecordingCommands()
        val service = CommandServiceImpl(TestPlatform(native))
        service.register(Any(), command("first") { aliases("shared") })

        assertThrows(IllegalArgumentException::class.java) {
            service.register(Any(), command("SHARED") {})
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.register(Any(), command("second") { aliases("FIRST") })
        }
        assertEquals(1, native.registerCalls)
    }

    @Test
    fun `failed native registration rolls reservations back`() {
        val native = RecordingCommands().apply { failNextRegistration = true }
        val service = CommandServiceImpl(TestPlatform(native))

        assertThrows(IllegalStateException::class.java) {
            service.register(Any(), command("hello") { aliases("hi") })
        }

        service.register(Any(), command("HI") {})
        assertEquals(2, native.registerCalls)
    }

    @Test
    fun `handler failures are logged and sanitized`() {
        val native = RecordingCommands()
        val platform = TestPlatform(native)
        val service = CommandServiceImpl(platform)
        service.register(Any(), command("hello") {
            executesAsync { CompletableFuture.failedFuture(IllegalStateException("private detail")) }
            suggestsAsync { CompletableFuture.failedFuture(IllegalArgumentException("private suggestion")) }
        })
        val sender = TestSender()

        native.dispatcher.execute(native.command, context(sender)).toCompletableFuture().join()
        val suggestions = native.dispatcher
            .suggest(native.command, context(sender)).toCompletableFuture().join()

        assertEquals(listOf(Component.text("Command execution failed.")), sender.messages)
        assertTrue(suggestions.isEmpty())
        assertEquals(2, platform.errors.size)
        assertTrue(platform.errors.any { it.message == "private detail" })
        assertTrue(platform.errors.any { it.message == "private suggestion" })
    }

    @Test
    fun `registration owner and service closing unregister natively once`() {
        val native = RecordingCommands()
        val service = CommandServiceImpl(TestPlatform(native))
        val firstOwner = Any()
        val registration = service.register(firstOwner, command("first") {})
        service.register(firstOwner, command("second") {})
        service.register(Any(), command("third") {})

        registration.close()
        registration.close()
        service.unregisterOwner(firstOwner)
        service.close()
        service.close()

        assertTrue(registration.isClosed)
        assertEquals(3, native.unregisterCalls)
        assertEquals(1, native.closeCalls)
        assertThrows(IllegalStateException::class.java) {
            service.register(Any(), command("late") {})
        }
    }

    private fun context(sender: TestSender) = CommandContext(sender, emptyList(), "hello", "")

    private class TestSender(
        private val permissions: Set<String> = emptySet(),
        private val console: Boolean = false,
    ) : CommandSender {
        val messages = mutableListOf<Component>()
        override val id: String = "sender"
        override val name: String = "Sender"
        override val isConsole: Boolean get() = console
        override fun hasPermission(permission: String): Boolean = permission in permissions
        override fun send(message: Component) { messages += message }
    }

    private class RecordingCommands : PlatformCommandAdapter {
        lateinit var command: CommandDefinition
        lateinit var dispatcher: PlatformCommandDispatcher
        var registerCalls = 0
        var unregisterCalls = 0
        var closeCalls = 0
        var failNextRegistration = false

        override fun register(
            owner: Any,
            command: CommandDefinition,
            dispatcher: PlatformCommandDispatcher,
        ): PlatformCommandRegistration {
            registerCalls++
            if (failNextRegistration) {
                failNextRegistration = false
                throw IllegalStateException("native failure")
            }
            this.command = command
            this.dispatcher = dispatcher
            return PlatformCommandRegistration { unregisterCalls++ }
        }

        override fun close() { closeCalls++ }
    }

    private class TestPlatform(
        override val commandAdapter: PlatformCommandAdapter,
    ) : PlatformAdapter {
        val errors = mutableListOf<Throwable>()
        override val type: PlatformType = PlatformType.BUKKIT
        override fun details(): Map<String, Any?> = emptyMap()
        override fun executeGlobal(task: Runnable) = task.run()
        override fun executeReply(recipient: Any, task: Runnable) = task.run()
        override fun log(owner: Any, level: LogLevel, message: String, error: Throwable?) {
            if (error != null) errors += error
        }
        override fun close() = Unit
    }
}
