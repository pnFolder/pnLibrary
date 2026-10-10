package ru.privatenull.pnlibrary.velocity.commands

import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.command.SimpleCommand
import com.velocitypowered.api.proxy.ProxyServer
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import ru.privatenull.pnlibrary.api.audiences.AudienceSender
import ru.privatenull.pnlibrary.api.commands.CommandContext
import ru.privatenull.pnlibrary.api.commands.CommandDefinition
import ru.privatenull.pnlibrary.api.commands.CommandSender
import ru.privatenull.pnlibrary.spi.commands.PlatformCommandAdapter
import ru.privatenull.pnlibrary.spi.commands.PlatformCommandDispatcher
import ru.privatenull.pnlibrary.spi.commands.PlatformCommandRegistration
import ru.privatenull.pnlibrary.velocity.VelocityAudienceAdapter
import java.util.LinkedHashSet
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicBoolean

/** Bridges portable pnLibrary commands to the native Velocity command manager. */
internal class VelocityCommandAdapter internal constructor(
    private val plugin: Any,
    private val registrar: VelocityNativeCommandRegistrar,
    private val audience: (CommandSource) -> AudienceSender,
) : PlatformCommandAdapter {

    /** Creates an adapter backed by Velocity's command manager. */
    constructor(plugin: Any, server: ProxyServer) : this(
        plugin = plugin,
        registrar = DefaultVelocityNativeCommandRegistrar(plugin, server),
        audience = { source ->
            requireNotNull(VelocityAudienceAdapter(server).sender(source)) {
                "Unsupported Velocity command source: ${source::class.java.name}"
            }
        },
    )

    private val closed = AtomicBoolean(false)
    private val registrations = LinkedHashSet<Registration>()

    /** Registers a portable command and tracks it until it is closed. */
    override fun register(
        owner: Any,
        command: CommandDefinition,
        dispatcher: PlatformCommandDispatcher,
    ): PlatformCommandRegistration {
        check(!closed.get()) { "Velocity command adapter is closed" }

        val nativeRegistration = registrar.register(
            command = command,
            execute = { source, arguments ->
                dispatcher.execute(command, context(source, command.name, arguments))
            },
            suggest = { source, arguments ->
                dispatcher.suggest(command, context(source, command.name, arguments))
            },
        )

        return Registration(nativeRegistration).also { registration ->
            synchronized(registrations) {
                registrations += registration
            }
        }
    }

    /** Closes all registered commands in reverse registration order. */
    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }

        synchronized(registrations) {
            registrations.toList().asReversed().forEach(Registration::close)
        }
    }

    /** Converts a native invocation into the portable command context. */
    private fun context(
        source: CommandSource,
        alias: String,
        arguments: Array<String>,
    ): CommandContext = CommandContext(
        sender = VelocityCommandSender(audience(source)),
        arguments = arguments.toList(),
        invokedAlias = alias,
        currentInput = arguments.lastOrNull().orEmpty(),
    )

    /** Owns one native registration and makes closing it idempotent. */
    private inner class Registration(
        private val native: PlatformCommandRegistration,
    ) : PlatformCommandRegistration {
        private val registrationClosed = AtomicBoolean(false)

        override fun close() {
            if (!registrationClosed.compareAndSet(false, true)) {
                return
            }

            synchronized(registrations) {
                registrations.remove(this)
            }
            native.close()
        }
    }
}

/** Adapts a portable command sender to the native Velocity audience. */
private class VelocityCommandSender(
    private val audience: AudienceSender,
) : CommandSender, AudienceSender by audience {
    override fun send(message: Component) = audience.sendMessage(message)

    override fun sendMessage(text: Component) = audience.sendMessage(text)

    override fun actionBar(text: Component) = audience.actionBar(text)

    override fun playSound(sound: Sound): Boolean = audience.playSound(sound)
}

/** Native command registration boundary used by [VelocityCommandAdapter]. */
internal interface VelocityNativeCommandRegistrar {
    /** Registers [command] and returns a handle that unregisters it. */
    fun register(
        command: CommandDefinition,
        execute: (CommandSource, Array<String>) -> Unit,
        suggest: (CommandSource, Array<String>) -> CompletionStage<List<String>>,
    ): PlatformCommandRegistration
}

/** Default registrar backed by Velocity's command manager. */
private class DefaultVelocityNativeCommandRegistrar(
    private val plugin: Any,
    private val server: ProxyServer,
) : VelocityNativeCommandRegistrar {

    override fun register(
        command: CommandDefinition,
        execute: (CommandSource, Array<String>) -> Unit,
        suggest: (CommandSource, Array<String>) -> CompletionStage<List<String>>,
    ): PlatformCommandRegistration {
        val metadata = server.commandManager.metaBuilder(command.name)
            .aliases(*command.aliases.toTypedArray())
            .plugin(plugin)
            .build()

        server.commandManager.register(metadata, object : SimpleCommand {
            override fun execute(invocation: SimpleCommand.Invocation) {
                execute(invocation.source(), invocation.arguments())
            }

            override fun suggestAsync(invocation: SimpleCommand.Invocation) =
                suggest(invocation.source(), invocation.arguments()).toCompletableFuture()
        })

        return PlatformCommandRegistration {
            server.commandManager.unregister(command.name)
        }
    }
}
