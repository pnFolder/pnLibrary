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
import ru.privatenull.pnlibrary.spi.commands.PlatformCommandRegistration
import ru.privatenull.pnlibrary.spi.commands.GenericPlatformCommandAdapter
import ru.privatenull.pnlibrary.spi.commands.NativeCommandRegistrar
import ru.privatenull.pnlibrary.velocity.VelocityAudienceAdapter
import java.util.concurrent.CompletionStage

/** Bridges portable pnLibrary commands to the native Velocity command manager. */
internal class VelocityCommandAdapter internal constructor(
    private val plugin: Any,
    private val nativeRegistrar: NativeCommandRegistrar<CommandSource>,
    private val audience: (CommandSource) -> AudienceSender,
) : GenericPlatformCommandAdapter<CommandSource>() {

    override val registrar: NativeCommandRegistrar<CommandSource> = nativeRegistrar

    /** Creates an adapter backed by Velocity's command manager. */
    constructor(plugin: Any, server: ProxyServer) : this(
        plugin = plugin,
        nativeRegistrar = DefaultVelocityNativeCommandRegistrar(plugin, server),
        audience = { source ->
            requireNotNull(VelocityAudienceAdapter(server).sender(source)) {
                "Unsupported Velocity command source: ${source::class.java.name}"
            }
        },
    )

    /** Converts a native invocation into the portable command context. */
    override fun context(
        source: CommandSource,
        alias: String,
        arguments: Array<String>,
    ): CommandContext = CommandContext(
        sender = VelocityCommandSender(audience(source)),
        arguments = arguments.toList(),
        invokedAlias = alias,
        currentInput = arguments.lastOrNull().orEmpty(),
    )

}

/** Adapts a portable command sender to the native Velocity audience. */
private class VelocityCommandSender(
    private val audience: AudienceSender,
) : CommandSender, AudienceSender by audience {
    /** Sends the command response through the wrapped audience. */
    override fun send(message: Component) = audience.sendMessage(message)

    /** Sends a chat message through the wrapped audience. */
    override fun sendMessage(text: Component) = audience.sendMessage(text)

    /** Displays a transient action-bar message. */
    override fun actionBar(text: Component) = audience.actionBar(text)

    /** Delegates sound playback and reports whether it is supported. */
    override fun playSound(sound: Sound): Boolean = audience.playSound(sound)
}

/** Default registrar backed by Velocity's command manager. */
private class DefaultVelocityNativeCommandRegistrar(
    private val plugin: Any,
    private val server: ProxyServer,
) : NativeCommandRegistrar<CommandSource> {

    /** Registers execution and asynchronous suggestions with Velocity. */
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
