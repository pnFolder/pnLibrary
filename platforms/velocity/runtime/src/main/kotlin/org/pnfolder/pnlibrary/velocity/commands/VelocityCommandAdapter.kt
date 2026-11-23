package org.pnfolder.pnlibrary.velocity.commands

import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.command.SimpleCommand
import com.velocitypowered.api.proxy.ProxyServer
import net.kyori.adventure.audience.Audience
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition
import org.pnfolder.pnlibrary.core.commands.proxy.proxyCommandContext
import org.pnfolder.pnlibrary.spi.commands.GenericPlatformCommandAdapter
import org.pnfolder.pnlibrary.spi.commands.NativeCommandRegistrar
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandRegistration
import org.pnfolder.pnlibrary.velocity.network.VelocityPlayerAdapter
import java.util.concurrent.CompletionStage

internal class VelocityCommandAdapter internal constructor(
    override val registrar: NativeCommandRegistrar<CommandSource>,
    private val audience: (CommandSource) -> Audience,
) : GenericPlatformCommandAdapter<CommandSource>() {

    constructor(server: ProxyServer, audiences: VelocityPlayerAdapter) : this(
        registrar = DefaultVelocityNativeCommandRegistrar(server),
        audience = { source ->
            requireNotNull(audiences.sender(source)) {
                "Unsupported Velocity command source: ${source::class.java.name}"
            }
        },
    )

    override fun context(
        source: CommandSource,
        alias: String,
        arguments: Array<String>,
    ): CommandContext = proxyCommandContext(audience(source), alias, arguments)
}

private class DefaultVelocityNativeCommandRegistrar(
    private val server: ProxyServer,
) : NativeCommandRegistrar<CommandSource> {

    override fun register(
        owner: Any,
        command: CommandDefinition,
        execute: (CommandSource, String, Array<String>) -> Unit,
        suggest: (CommandSource, String, Array<String>) -> CompletionStage<List<String>>,
    ): PlatformCommandRegistration {
        require(server.pluginManager.fromInstance(owner).isPresent) {
            "Command owner must be a registered Velocity plugin"
        }
        for (name in listOf(command.name) + command.aliases) {
            require(!server.commandManager.hasCommand(name)) {
                "Command name or alias '$name' is already registered on Velocity"
            }
        }
        val metadata = server.commandManager.metaBuilder(command.name)
            .aliases(*command.aliases.toTypedArray())
            .plugin(owner)
            .build()

        server.commandManager.register(metadata, object : SimpleCommand {
            override fun execute(invocation: SimpleCommand.Invocation) =
                execute(invocation.source(), invocation.alias(), invocation.arguments())

            override fun suggestAsync(invocation: SimpleCommand.Invocation) =
                suggest(invocation.source(), invocation.alias(), invocation.arguments()).toCompletableFuture()
        })

        return PlatformCommandRegistration { server.commandManager.unregister(metadata) }
    }
}
