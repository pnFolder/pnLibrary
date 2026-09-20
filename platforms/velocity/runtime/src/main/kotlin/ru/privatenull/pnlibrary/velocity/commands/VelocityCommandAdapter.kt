package ru.privatenull.pnlibrary.velocity.commands

import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.command.SimpleCommand
import com.velocitypowered.api.proxy.ProxyServer
import net.kyori.adventure.text.Component
import net.kyori.adventure.sound.Sound
import ru.privatenull.pnlibrary.api.commands.CommandContext
import ru.privatenull.pnlibrary.api.commands.CommandDefinition
import ru.privatenull.pnlibrary.api.commands.CommandSender
import ru.privatenull.pnlibrary.api.audiences.AudienceSender
import ru.privatenull.pnlibrary.velocity.VelocityAudienceAdapter
import ru.privatenull.pnlibrary.spi.commands.PlatformCommandAdapter
import ru.privatenull.pnlibrary.spi.commands.PlatformCommandDispatcher
import ru.privatenull.pnlibrary.spi.commands.PlatformCommandRegistration
import java.util.LinkedHashSet
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicBoolean

/** Thin Velocity transport for portable commands and asynchronous suggestions. */
internal class VelocityCommandAdapter internal constructor(
    private val plugin: Any,
    private val registrar: VelocityNativeCommandRegistrar,
    private val audience: (CommandSource) -> AudienceSender,
) : PlatformCommandAdapter {
    constructor(plugin: Any, server: ProxyServer) : this(
        plugin,
        DefaultVelocityNativeCommandRegistrar(plugin, server),
        { source -> requireNotNull(VelocityAudienceAdapter(server).sender(source)) },
    )

    private val closed = AtomicBoolean(false)
    private val registrations = LinkedHashSet<Registration>()

    override fun register(
        owner: Any,
        command: CommandDefinition,
        dispatcher: PlatformCommandDispatcher,
    ): PlatformCommandRegistration {
        check(!closed.get()) { "Velocity command adapter is closed" }
        val native = registrar.register(
            command,
            { source, arguments ->
                dispatcher.execute(command, context(source, command.name, arguments))
            },
            { source, arguments ->
                dispatcher.suggest(command, context(source, command.name, arguments))
            },
        )
        return Registration(native).also { synchronized(registrations) { registrations += it } }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val current = synchronized(registrations) { registrations.toList() }
        current.asReversed().forEach(Registration::close)
    }

    private fun context(
        source: CommandSource,
        alias: String,
        arguments: Array<String>,
    ) = CommandContext(
        sender = VelocityCommandSender(audience(source)),
        arguments = arguments.toList(),
        invokedAlias = alias,
        currentInput = arguments.lastOrNull().orEmpty(),
    )

    private inner class Registration(
        private val native: PlatformCommandRegistration,
    ) : PlatformCommandRegistration {
        private val registrationClosed = AtomicBoolean(false)
        override fun close() {
            if (!registrationClosed.compareAndSet(false, true)) return
            synchronized(registrations) { registrations.remove(this) }
            native.close()
        }
    }
}

private class VelocityCommandSender(
    private val audience: AudienceSender,
) : CommandSender, AudienceSender by audience {
    override fun send(message: Component) = audience.sendMessage(message)
    override fun sendMessage(text: Component) = audience.sendMessage(text)
    override fun actionBar(text: Component) = audience.actionBar(text)
    override fun playSound(sound: Sound): Boolean = audience.playSound(sound)
}

internal interface VelocityNativeCommandRegistrar {
    fun register(
        command: CommandDefinition,
        execute: (CommandSource, Array<String>) -> Unit,
        suggest: (CommandSource, Array<String>) -> CompletionStage<List<String>>,
    ): PlatformCommandRegistration
}

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
            override fun execute(
                invocation: SimpleCommand.Invocation
            ) = execute(invocation.source(), invocation.arguments())

            override fun suggestAsync(
                invocation: SimpleCommand.Invocation
            ) = suggest(invocation.source(), invocation.arguments()).toCompletableFuture()
        })
        return PlatformCommandRegistration { server.commandManager.unregister(command.name) }
    }
}
