package org.pnfolder.pnlibrary.velocity.commands

import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.command.SimpleCommand
import com.velocitypowered.api.proxy.ProxyServer
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.audiences.AudienceSender
import org.pnfolder.pnlibrary.api.audiences.Delivery
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.CommandSender
import org.pnfolder.pnlibrary.core.commands.ProxyCommandNames
import org.pnfolder.pnlibrary.spi.commands.GenericPlatformCommandAdapter
import org.pnfolder.pnlibrary.spi.commands.NativeCommandRegistrar
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandRegistration
import org.pnfolder.pnlibrary.velocity.VelocityAudienceAdapter
import java.util.concurrent.CompletionStage

/** Bridges portable pnLibrary commands to the native Velocity command manager. */
internal class VelocityCommandAdapter internal constructor(
    override val registrar: NativeCommandRegistrar<CommandSource>,
    private val audience: (CommandSource) -> AudienceSender,
) : GenericPlatformCommandAdapter<CommandSource>() {

    /** Creates an adapter backed by Velocity's command manager. */
    constructor(server: ProxyServer, audiences: VelocityAudienceAdapter) : this(
        registrar = DefaultVelocityNativeCommandRegistrar(server),
        audience = { source ->
            requireNotNull(audiences.sender(source)) {
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
        sender = VelocityCommandSender(audience(source), ProxyCommandNames.owns(alias)),
        arguments = arguments.toList(),
        invokedAlias = alias,
        currentInput = arguments.lastOrNull().orEmpty(),
    )

}

/** Adapts a portable command sender to the native Velocity audience. */
private class VelocityCommandSender(
    private val audience: AudienceSender,
    private val proxyControl: Boolean,
) : CommandSender, AudienceSender by audience {
    /** Sends the command response through the wrapped audience. */
    override fun send(message: Component) = audience.sendMessage(shown(message))

    // Explicit overrides preserve native delivery instead of CommandSender's fallback defaults.
    override fun sendMessage(text: Component) = audience.sendMessage(shown(text))

    private fun shown(message: Component) = if (proxyControl) ProxyCommandNames.rewrite(message) else message
    override fun actionBar(text: Component) = audience.actionBar(text)
    override fun playSound(sound: Sound): Delivery = audience.playSound(sound)
}

/** Default registrar backed by Velocity's command manager. */
private class DefaultVelocityNativeCommandRegistrar(
    private val server: ProxyServer,
) : NativeCommandRegistrar<CommandSource> {

    /** Registers execution and asynchronous suggestions with Velocity. */
    override fun register(
        owner: Any,
        command: CommandDefinition,
        execute: (CommandSource, String, Array<String>) -> Unit,
        suggest: (CommandSource, String, Array<String>) -> CompletionStage<List<String>>,
    ): PlatformCommandRegistration {
        require(server.pluginManager.fromInstance(owner).isPresent) {
            "Command owner must be a registered Velocity plugin"
        }
        (listOf(command.name) + command.aliases).forEach { name ->
            require(!server.commandManager.hasCommand(name)) {
                "Command name or alias '$name' is already registered on Velocity"
            }
        }
        val metadata = server.commandManager.metaBuilder(command.name)
            .aliases(*command.aliases.toTypedArray())
            .plugin(owner)
            .build()

        server.commandManager.register(metadata, object : SimpleCommand {
            override fun execute(invocation: SimpleCommand.Invocation) {
                execute(invocation.source(), invocation.alias(), invocation.arguments())
            }

            override fun suggestAsync(invocation: SimpleCommand.Invocation) =
                suggest(invocation.source(), invocation.alias(), invocation.arguments()).toCompletableFuture()
        })

        return PlatformCommandRegistration {
            server.commandManager.unregister(metadata)
        }
    }
}
