package org.pnfolder.pnlibrary.bungee.commands

import net.md_5.bungee.api.plugin.Command
import net.md_5.bungee.api.plugin.Plugin
import net.md_5.bungee.api.plugin.TabExecutor
import org.pnfolder.pnlibrary.api.audiences.AudienceSender
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.CommandDefinition
import org.pnfolder.pnlibrary.bungee.BungeeAudienceAdapter
import org.pnfolder.pnlibrary.core.commands.PROXY_SUGGESTION_MILLIS
import org.pnfolder.pnlibrary.core.commands.proxy.proxyCommandContext
import org.pnfolder.pnlibrary.core.commands.suggestionsWithin
import org.pnfolder.pnlibrary.spi.commands.GenericPlatformCommandAdapter
import org.pnfolder.pnlibrary.spi.commands.NativeCommandRegistrar
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandRegistration
import java.util.concurrent.CompletionStage
import net.md_5.bungee.api.CommandSender as NativeCommandSender

/**
 * Registers portable commands with BungeeCord and owns their native registrations.
 *
 * Native tab completion is synchronous, so suggestions wait at most [PROXY_SUGGESTION_MILLIS].
 * Closing this adapter unregisters commands in reverse registration order.
 */
internal class BungeeCommandAdapter internal constructor(
    private val nativeRegistrar: NativeCommandRegistrar<NativeCommandSender>,
    private val audience: (NativeCommandSender) -> AudienceSender,
) : GenericPlatformCommandAdapter<NativeCommandSender>() {

    override val registrar: NativeCommandRegistrar<NativeCommandSender> = nativeRegistrar
    /** Registers commands in the plugin manager of [plugin]'s proxy. */
    constructor(plugin: Plugin) : this(
        nativeRegistrar = DefaultBungeeNativeCommandRegistrar(),
        { sender -> requireNotNull(BungeeAudienceAdapter(plugin).sender(sender)) },
    )

    override fun context(
        source: NativeCommandSender,
        alias: String,
        arguments: Array<String>,
    ): CommandContext = proxyCommandContext(audience(source), alias, arguments)
}

/** Installs command and tab-completion handlers in the proxy plugin manager. */
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

/** One portable command as a BungeeCord command; tab completion waits at most [PROXY_SUGGESTION_MILLIS]. */
private class NativeBungeeCommand(
    private val definition: CommandDefinition,
    private val onExecute: (NativeCommandSender, String, Array<String>) -> Unit,
    private val onSuggest: (NativeCommandSender, String, Array<String>) -> CompletionStage<List<String>>,
) : Command(definition.name, null, *definition.aliases.toTypedArray()), TabExecutor {
    override fun execute(sender: NativeCommandSender, args: Array<String>) = onExecute(sender, definition.name, args)

    override fun onTabComplete(sender: NativeCommandSender, args: Array<String>): Iterable<String> =
        onSuggest(sender, definition.name, args).suggestionsWithin(PROXY_SUGGESTION_MILLIS)
}
