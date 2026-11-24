package org.pnfolder.pnlibrary.bungee.commands

import net.kyori.adventure.audience.Audience
import net.md_5.bungee.api.plugin.Command
import net.md_5.bungee.api.plugin.Plugin
import net.md_5.bungee.api.plugin.TabExecutor
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition
import org.pnfolder.pnlibrary.bungee.network.BungeeAudienceAdapter
import org.pnfolder.pnlibrary.core.commands.engine.PROXY_SUGGESTION_MILLIS
import org.pnfolder.pnlibrary.core.commands.engine.suggestionsWithin
import org.pnfolder.pnlibrary.core.commands.proxy.proxyCommandContext
import org.pnfolder.pnlibrary.spi.commands.GenericPlatformCommandAdapter
import org.pnfolder.pnlibrary.spi.commands.NativeCommandRegistrar
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandRegistration
import java.util.concurrent.CompletionStage
import net.md_5.bungee.api.CommandSender as NativeCommandSender

internal class BungeeCommandAdapter(
    nativeRegistrar: NativeCommandRegistrar<NativeCommandSender>,
    private val audience: (NativeCommandSender) -> Audience,
) : GenericPlatformCommandAdapter<NativeCommandSender>() {

    constructor(audiences: BungeeAudienceAdapter) : this(
        nativeRegistrar = DefaultBungeeNativeCommandRegistrar(),
        audience = { sender -> requireNotNull(audiences.of(sender)) },
    )

    override val registrar: NativeCommandRegistrar<NativeCommandSender> = nativeRegistrar

    override fun context(
        source: NativeCommandSender,
        alias: String,
        arguments: Array<String>,
    ): CommandContext = proxyCommandContext(audience(source), alias, arguments)
}

private class DefaultBungeeNativeCommandRegistrar : NativeCommandRegistrar<NativeCommandSender> {
    override fun register(
        owner: Any,
        command: CommandDefinition,
        execute: (NativeCommandSender, String, Array<String>) -> Unit,
        suggest: (NativeCommandSender, String, Array<String>) -> CompletionStage<List<String>>,
    ): PlatformCommandRegistration {
        val ownerPlugin = owner as? Plugin
            ?: throw IllegalArgumentException("Command owner must be a Bungee plugin")
        val native = NativeBungeeCommand(command, execute, suggest)
        ownerPlugin.proxy.pluginManager.registerCommand(ownerPlugin, native)
        return PlatformCommandRegistration { ownerPlugin.proxy.pluginManager.unregisterCommand(native) }
    }
}

private class NativeBungeeCommand(
    private val definition: CommandDefinition,
    private val onExecute: (NativeCommandSender, String, Array<String>) -> Unit,
    private val onSuggest: (NativeCommandSender, String, Array<String>) -> CompletionStage<List<String>>,
) : Command(definition.name, null, *definition.aliases.toTypedArray()), TabExecutor {

    override fun execute(sender: NativeCommandSender, args: Array<String>) =
        onExecute(sender, definition.name, args)

    // Bungee tab completion is synchronous; do not block the proxy beyond the suggestion budget.
    override fun onTabComplete(sender: NativeCommandSender, args: Array<String>): Iterable<String> =
        onSuggest(sender, definition.name, args).suggestionsWithin(PROXY_SUGGESTION_MILLIS)
}
