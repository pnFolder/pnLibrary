package org.pnfolder.pnlibrary.bungee.commands

import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.md_5.bungee.api.plugin.Command
import net.md_5.bungee.api.plugin.Plugin
import net.md_5.bungee.api.plugin.TabExecutor
import org.pnfolder.pnlibrary.api.audiences.AudienceSender
import org.pnfolder.pnlibrary.api.audiences.Delivery
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.CommandSender
import org.pnfolder.pnlibrary.bungee.BungeeAudienceAdapter
import org.pnfolder.pnlibrary.core.commands.ProxyCommandNames
import org.pnfolder.pnlibrary.spi.commands.GenericPlatformCommandAdapter
import org.pnfolder.pnlibrary.spi.commands.NativeCommandRegistrar
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandRegistration
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit
import net.md_5.bungee.api.CommandSender as NativeCommandSender

/**
 * Registers portable commands with BungeeCord and owns their native registrations.
 *
 * Native tab completion is synchronous, so suggestions have a bounded one-second wait.
 * Closing this adapter unregisters commands in reverse registration order.
 */
internal class BungeeCommandAdapter internal constructor(
    private val nativeRegistrar: NativeCommandRegistrar<NativeCommandSender>,
    private val audience: (NativeCommandSender) -> AudienceSender,
) : GenericPlatformCommandAdapter<NativeCommandSender>() {

    override val registrar: NativeCommandRegistrar<NativeCommandSender> = nativeRegistrar
    /** Creates the native command registrar and audience bridge. */
    constructor(plugin: Plugin) : this(
        nativeRegistrar = DefaultBungeeNativeCommandRegistrar(),
        { sender -> requireNotNull(BungeeAudienceAdapter(plugin).sender(sender)) },
    )

    /** Converts native invocation arguments to the portable command context. */
    override fun context(
        source: NativeCommandSender,
        alias: String,
        arguments: Array<String>,
    ): CommandContext = CommandContext(
        sender = BungeeCommandSender(audience(source), ProxyCommandNames.owns(alias)),
        arguments = arguments.toList(),
        invokedAlias = alias,
        currentInput = arguments.lastOrNull().orEmpty(),
    )

}

/** Delegates portable command replies to the native audience bridge. */
private class BungeeCommandSender(
    private val audience: AudienceSender,
    private val proxyControl: Boolean,
) : CommandSender, AudienceSender by audience {

    override fun send(message: Component) = audience.sendMessage(shown(message))

    override fun sendMessage(text: Component) = audience.sendMessage(shown(text))

    private fun shown(message: Component) = if (proxyControl) ProxyCommandNames.rewrite(message) else message

    override fun actionBar(text: Component) = audience.actionBar(text)

    override fun playSound(sound: Sound): Delivery = audience.playSound(sound)
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
        val executeHandler = execute
        val suggestionHandler = suggest
        val native = object : Command(command.name, null, *command.aliases.toTypedArray()), TabExecutor {
            override fun execute(sender: NativeCommandSender, args: Array<String>) = executeHandler(sender, command.name, args)
            override fun onTabComplete(sender: NativeCommandSender, args: Array<String>): Iterable<String> =
                suggestionHandler(sender, command.name, args).toCompletableFuture().get(1, TimeUnit.SECONDS)
        }
        ownerPlugin.proxy.pluginManager.registerCommand(ownerPlugin, native)
        return PlatformCommandRegistration { ownerPlugin.proxy.pluginManager.unregisterCommand(native) }
    }
}
