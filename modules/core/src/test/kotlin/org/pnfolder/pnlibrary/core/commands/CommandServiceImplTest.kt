package org.pnfolder.pnlibrary.core.commands

import net.kyori.adventure.text.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.commands.ArgumentType
import org.pnfolder.pnlibrary.api.commands.BadUsage
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.CommandError
import org.pnfolder.pnlibrary.api.commands.CommandErrorHandler
import org.pnfolder.pnlibrary.api.commands.CommandSender
import org.pnfolder.pnlibrary.api.commands.InvalidArgument
import org.pnfolder.pnlibrary.api.commands.NoPermission
import org.pnfolder.pnlibrary.api.commands.command
import org.pnfolder.pnlibrary.api.commands.ensure
import org.pnfolder.pnlibrary.api.commands.fail
import org.pnfolder.pnlibrary.api.logging.LogLevel
import org.pnfolder.pnlibrary.api.platform.PlatformType
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandAdapter
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandDispatcher
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandRegistration
import org.pnfolder.pnlibrary.spi.platform.PlatformAdapter
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CommandServiceImplTest {
    @Test
    fun `diagnostic snapshots stay coherent during concurrent registration`() {
        val native = RecordingCommands()
        val platform = TestPlatform(native)
        val service = CommandServiceImpl(native, platform)
        val owner = Any()
        val executor = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        try {
            val writer = executor.submit {
                start.await()
                repeat(500) { service.register(owner, command("temporary") {}).close() }
            }
            val reader = executor.submit {
                start.await()
                repeat(1000) {
                    val snapshot = service.diagnosticSnapshot()
                    assertEquals((snapshot["registeredNames"] as List<*>).size, snapshot["registeredCount"])
                }
            }
            start.countDown()
            writer.get(10, TimeUnit.SECONDS)
            reader.get(10, TimeUnit.SECONDS)
            assertEquals(0, service.diagnosticSnapshot()["registeredCount"])
        } finally {
            executor.shutdownNow()
            service.close()
        }
    }

    @Test
    fun `builder overload registers a command without manual definition construction`() {
        val native = RecordingCommands()
        val platform = TestPlatform(native)
        val service = CommandServiceImpl(platform.commandAdapter, platform)

        val registration = service.register(Any(), "hello") {
            executes { }
        }

        assertEquals("hello", registration.command.name)
        assertFalse(registration.isClosed)
        registration.close()
        assertTrue(registration.isClosed)
    }

    @Test
    fun `service dispatches a tree leaf and sanitizes predicate failures`() {
        val native = RecordingCommands()
        val platform = TestPlatform(native)
        val service = CommandServiceImpl(platform.commandAdapter, platform)
        var selected: String? = null
        service.register(Any(), command("group") {
            argument("group", ArgumentType.string()) {
                literal("show") { executes { selected = it.get("group") } }
                literal("broken") {
                    availableIf { error("private predicate") }
                    executes { }
                }
            }
        })
        val sender = TestSender()

        native.dispatcher.execute(
            native.command,
            CommandContext(sender, listOf("builders", "show"), "group", "show"),
        ).toCompletableFuture().join()
        native.dispatcher.execute(
            native.command,
            CommandContext(sender, listOf("builders", "broken"), "group", "broken"),
        ).toCompletableFuture().join()

        assertEquals("builders", selected)
        assertEquals(listOf(Component.text("Command execution failed.")), sender.messages)
        assertTrue(platform.errors.any { it.message == "private predicate" })
    }

    @Test
    fun `authorized execution and suggestions reach the portable definition`() {
        val native = RecordingCommands()
        val service = CommandServiceImpl(TestPlatform(native).commandAdapter, TestPlatform(native))
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
        val service = CommandServiceImpl(TestPlatform(native).commandAdapter, TestPlatform(native))
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
        assertEquals(listOf(Component.text("Недостаточно прав для этой команды.")), denied.messages)
    }

    @Test
    fun `a command can replace the default no-permission message with its own text`() {
        val native = RecordingCommands()
        val service = CommandServiceImpl(TestPlatform(native).commandAdapter, TestPlatform(native))
        service.register(Any(), command("hello") {
            permission("example.hello")
            noPermissionMessage("Нужна покупка доступа: /shop buy hello")
            executes { }
        })
        val denied = TestSender()

        native.dispatcher.execute(native.command, context(denied)).toCompletableFuture().join()

        assertEquals(listOf(Component.text("Нужна покупка доступа: /shop buy hello")), denied.messages)
    }

    @Test
    fun `name and alias collisions fail before native registration`() {
        val native = RecordingCommands()
        val service = CommandServiceImpl(TestPlatform(native).commandAdapter, TestPlatform(native))
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
        val service = CommandServiceImpl(TestPlatform(native).commandAdapter, TestPlatform(native))

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
        val service = CommandServiceImpl(platform.commandAdapter, platform)
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
        val service = CommandServiceImpl(TestPlatform(native).commandAdapter, TestPlatform(native))
        val firstOwner = Any()
        val registration = service.register(firstOwner, command("first") {})
        service.register(firstOwner, command("second") {})
        service.register(Any(), command("third") {})

        registration.close()
        registration.close()
        service.unregisterOwner(firstOwner)
        assertEquals(setOf("first", "second"), native.unregisteredNames)
        assertFalse(native.unregisteredNames.contains("third"))
        service.close()
        service.close()

        assertTrue(registration.isClosed)
        assertEquals(3, native.unregisterCalls)
        assertEquals(1, native.closeCalls)
        assertThrows(IllegalStateException::class.java) {
            service.register(Any(), command("late") {})
        }
    }

    @Test
    fun `one native close failure does not leak remaining registrations`() {
        val native = RecordingCommands().apply { failingCloseName = "second" }
        val platform = TestPlatform(native)
        val service = CommandServiceImpl(platform.commandAdapter, platform)
        service.register(Any(), command("first") {})
        service.register(Any(), command("second") {})
        service.register(Any(), command("third") {})

        service.close()

        assertEquals(setOf("first", "second", "third"), native.unregisteredNames)
        assertEquals(1, native.closeCalls)
        assertTrue(platform.errors.any { it.message == "close failed" })
    }

    @Test
    fun `a subcommand message wins over the command message and the configured default`() {
        val native = RecordingCommands()
        val service = CommandServiceImpl(
            TestPlatform(native).commandAdapter, TestPlatform(native), "Настроенный текст из config.yml",
        )
        service.register(Any(), command("hello") {
            noPermissionMessage("Текст команды")
            executes { }
            literal("vip") {
                permission("example.vip")
                noPermissionMessage("Это только для VIP")
                executes { }
            }
            literal("staff") {
                permission("example.staff")
                executes { }
            }
        })
        val sender = TestSender()

        native.dispatcher.execute(native.command, context(sender, "vip")).toCompletableFuture().join()
        native.dispatcher.execute(native.command, context(sender, "staff")).toCompletableFuture().join()

        assertEquals(
            listOf(Component.text("Это только для VIP"), Component.text("Текст команды")),
            sender.messages,
        )
    }

    @Test
    fun `the configured default replaces the built in text when nothing more specific is set`() {
        val native = RecordingCommands()
        val service = CommandServiceImpl(
            TestPlatform(native).commandAdapter, TestPlatform(native), "Настроенный текст из config.yml",
        )
        service.register(Any(), command("hello") {
            permission("example.hello")
            executes { }
        })
        val sender = TestSender()

        native.dispatcher.execute(native.command, context(sender)).toCompletableFuture().join()

        assertEquals(listOf(Component.text("Настроенный текст из config.yml")), sender.messages)
    }

    private fun say(text: String) = CommandErrorHandler<CommandError> { context, error ->
        context.sender.send(Component.text(error.format(text)))
    }

    private fun service(native: RecordingCommands, standard: StandardCommandFeedback = StandardCommandFeedback()) =
        CommandServiceImpl(TestPlatform(native).commandAdapter, TestPlatform(native), standard)

    private fun run(native: RecordingCommands, sender: TestSender, vararg arguments: String) =
        native.dispatcher.execute(native.command, context(sender, *arguments)).toCompletableFuture().join()

    private fun textOf(component: Component) = (component as net.kyori.adventure.text.TextComponent).content()

    /** An error of the plugin's own: fields instead of a map of strings, and its text built from them. */
    private class NoMoney(val price: Int) : CommandError() {
        override fun placeholders() = mapOf("price" to price)
    }

    /** An error that knows what to say when nobody answers it. */
    private class ShopClosed : CommandError("Магазин закрыт")

    private class Unexplained : CommandError()

    @Test
    fun `a handler receives the typed error and the context, so the sender is right there`() {
        val native = RecordingCommands()
        val seen = mutableListOf<String>()
        service(native).register(Any(), command("hello") {
            executes { }
            onError<NoPermission> { error -> seen += "command:${sender.id}:${error.permission}" }
            literal("vip") {
                permission("example.vip")
                onError<NoPermission> { error ->
                    seen += "node:${error.permission}"
                    sender.send(Component.text("Свой ответ"))
                }
                executes { }
            }
            literal("staff") { permission("example.staff"); executes { } }
        })
        val sender = TestSender()

        run(native, sender, "vip")
        run(native, sender, "staff")

        assertEquals(listOf("node:example.vip", "command:sender:example.staff"), seen)
        assertEquals(listOf(Component.text("Свой ответ")), sender.messages)
    }

    @Test
    fun `a handler that fails is logged and the next level answers, never an error shown to the sender`() {
        val native = RecordingCommands()
        service(native, StandardCommandFeedback(deniedMessage = "Настроенный текст")).register(Any(), command("hello") {
            permission("example.hello")
            executes { }
            onError<NoPermission> { error("handler is broken") }
        })
        val sender = TestSender()

        run(native, sender)

        assertEquals(listOf(Component.text("Настроенный текст")), sender.messages)
    }

    @Test
    fun `the chain goes from subcommand to command to module to plugin to the library standard`() {
        val native = RecordingCommands()
        val plugin = CommandFeedbackScope()
        val module = CommandFeedbackScope(plugin)
        module.handlers[NoPermission::class.java] = say("Текст модуля")
        plugin.handlers[NoPermission::class.java] = say("Текст плагина")
        val sender = TestSender()
        service(native, StandardCommandFeedback(deniedMessage = "Стандарт библиотеки")).register(Any(), command("hello") {
            executes { }
            literal("node") { permission("p.node"); noPermissionMessage("Текст узла"); executes { } }
            literal("module") { permission("p.module"); executes { } }
        }, module)

        run(native, sender, "node")
        run(native, sender, "module")
        module.handlers.remove(NoPermission::class.java)
        run(native, sender, "module")
        plugin.handlers.remove(NoPermission::class.java)
        run(native, sender, "module")

        assertEquals(
            listOf("Текст узла", "Текст модуля", "Текст плагина", "Стандарт библиотеки").map(Component::text),
            sender.messages,
        )
    }

    @Test
    fun `the standard answer can add a sound and reshape the usage line`() {
        val native = RecordingCommands()
        val sound = net.kyori.adventure.sound.Sound.sound(net.kyori.adventure.key.Key.key("entity.villager.no"), net.kyori.adventure.sound.Sound.Source.MASTER,
            1f, 1f)
        service(native, StandardCommandFeedback(deniedMessage = "Нет прав", deniedSound = sound, usageFormat = "Подсказка → {usage}"))
            .register(Any(), command("hello") { permission("p.hello"); executes { }; literal("a") { executes { } } })
        val denied = TestSender()
        val allowed = TestSender(setOf("p.hello"))

        run(native, denied)
        run(native, allowed, "zzz")

        assertEquals(listOf(Component.text("Нет прав")), denied.messages)
        assertEquals(listOf(sound), denied.sounds)
        assertTrue(textOf(allowed.messages.single()).startsWith("Подсказка → /hello"))
    }

    @Test
    fun `a handler for a base class answers every error derived from it until something more specific is set`() {
        val native = RecordingCommands()
        val plain = TestSender()
        service(native).register(Any(), command("hello") {
            literal("buy") { argument("amount", ArgumentType.integer(1, 5)) { executes { } } }
        })
        run(native, plain, "buy", "99")
        assertEquals("Неверное значение «99» для amount. /hello buy <amount>", textOf(plain.messages.single()))

        val caught = TestSender()
        val second = RecordingCommands()
        service(second).register(Any(), command("hello") {
            onError<BadUsage> { error -> sender.send(Component.text("общий: ${error.javaClass.simpleName}")) }
            literal("buy") { argument("amount", ArgumentType.integer(1, 5)) { executes { } } }
        })
        run(second, caught, "buy", "99")
        run(second, caught, "zzz")
        run(second, caught, "buy")
        assertEquals(listOf("общий: InvalidArgument", "общий: UnknownArgument", "общий: IncompleteCommand"), caught.messages.map(::textOf))

        val exact = TestSender()
        val third = RecordingCommands()
        service(third).register(Any(), command("hello") {
            onError<BadUsage> { sender.send(Component.text("общий")) }
            onError<InvalidArgument>("точный: {argument}={value}")
            literal("buy") { argument("amount", ArgumentType.integer(1, 5)) { executes { } } }
        })
        run(third, exact, "buy", "x")
        assertEquals("точный: amount=x", textOf(exact.messages.single()))
    }

    @Test
    fun `a player argument that names nobody is reported as player not found`() {
        val native = RecordingCommands()
        service(native).register(Any(), command("hello") {
            literal("kick") { argument("who", ArgumentType.player { null }) { executes { } } }
        })
        val sender = TestSender()

        run(native, sender, "kick", "Steve")

        assertEquals("Игрок Steve сейчас не в сети.", textOf(sender.messages.single()))
    }

    @Test
    fun `player only and console only commands answer the wrong sender`() {
        val native = RecordingCommands()
        service(native).register(Any(), command("hello") {
            literal("game") { playerOnly(); executes { } }
            literal("admin") { consoleOnly(); executes { } }
        })
        val console = TestSender(console = true)
        val player = TestSender()

        run(native, console, "game")
        run(native, player, "admin")

        assertEquals("Эту команду можно использовать только в игре.", textOf(console.messages.single()))
        assertEquals("Эту команду можно использовать только из консоли.", textOf(player.messages.single()))
    }

    @Test
    fun `a command stops with an error of its own and any level can answer it`() {
        val native = RecordingCommands()
        val module = CommandFeedbackScope()
        module.handlers[NoMoney::class.java] = CommandErrorHandler<NoMoney> { context, error ->
            context.sender.send(Component.text("Не хватает ${error.price} монет"))
        }
        service(native).register(Any(), command("hello") {
            executes { context -> context.ensure(false) { NoMoney(50) } }
            literal("closed") { executes { context -> context.fail(ShopClosed()) } }
            literal("odd") { executes { context -> context.fail(Unexplained()) } }
            literal("fine") { executes { context -> context.ensure(true) { NoMoney(1) } } }
        }, module)
        val sender = TestSender()

        run(native, sender)
        run(native, sender, "closed")
        run(native, sender, "odd")
        run(native, sender, "fine")

        assertEquals(listOf("Не хватает 50 монет", "Магазин закрыт", "Не удалось выполнить команду."), sender.messages.map(::textOf))
    }

    private fun context(sender: TestSender, vararg arguments: String) =
        CommandContext(sender, arguments.toList(), "hello", "")

    private class TestSender(
        private val permissions: Set<String> = emptySet(),
        private val console: Boolean = false,
    ) : CommandSender {
        val messages = mutableListOf<Component>()
        val sounds = mutableListOf<net.kyori.adventure.sound.Sound>()
        override fun playSound(sound: net.kyori.adventure.sound.Sound): Boolean { sounds += sound; return true }
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
        var failingCloseName: String? = null
        val unregisteredNames = linkedSetOf<String>()

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
            return PlatformCommandRegistration {
                unregisterCalls++
                unregisteredNames += command.name
                if (command.name == failingCloseName) throw IllegalStateException("close failed")
            }
        }

        override fun close() { closeCalls++ }
    }

    private class TestPlatform(
        override val commandAdapter: PlatformCommandAdapter,
    ) : PlatformAdapter {
        val errors = mutableListOf<Throwable>()
        override val type: PlatformType = PlatformType.BUKKIT
        override val dataFolder = Files.createTempDirectory("pnlibrary-command-test")
        override fun diagnosticDetails(includeSensitive: Boolean): Map<String, Any?> = emptyMap()
        override fun executeGlobal(task: Runnable) = task.run()
        override fun executeReply(recipient: Any, task: Runnable) = task.run()
        override fun log(owner: Any, level: LogLevel, message: String, error: Throwable?) {
            if (error != null) errors += error
        }
        override fun close() = Unit
    }
}
