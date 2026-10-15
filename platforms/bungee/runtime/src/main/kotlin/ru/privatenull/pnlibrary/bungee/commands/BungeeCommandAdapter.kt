package ru.privatenull.pnlibrary.bungee.commands

import net.kyori.adventure.text.Component
import net.kyori.adventure.sound.Sound
import net.md_5.bungee.api.CommandSender as NativeCommandSender
import net.md_5.bungee.api.plugin.Command
import net.md_5.bungee.api.plugin.Plugin
import net.md_5.bungee.api.plugin.TabExecutor
import ru.privatenull.pnlibrary.api.commands.CommandContext
import ru.privatenull.pnlibrary.api.commands.CommandDefinition
import ru.privatenull.pnlibrary.api.commands.CommandSender
import ru.privatenull.pnlibrary.api.audiences.AudienceSender
import ru.privatenull.pnlibrary.bungee.BungeeAudienceAdapter
import ru.privatenull.pnlibrary.spi.commands.PlatformCommandRegistration
import ru.privatenull.pnlibrary.spi.commands.GenericPlatformCommandAdapter
import ru.privatenull.pnlibrary.spi.commands.NativeCommandRegistrar

/**
 * Registers portable commands with BungeeCord and owns their native registrations.
 *
 * Native tab completion is synchronous, so suggestions have a bounded one-second wait.
 * Closing this adapter unregisters commands in reverse registration order.
 */
internal class BungeeCommandAdapter internal constructor(
    private val plugin: Any,
    private val nativeRegistrar: NativeCommandRegistrar<NativeCommandSender>,
    private val audience: (NativeCommandSender) -> AudienceSender,
) : GenericPlatformCommandAdapter<NativeCommandSender>() {

    override val registrar: NativeCommandRegistrar<NativeCommandSender> = nativeRegistrar
    /** Creates the native command registrar and audience bridge. */
    constructor(plugin: Plugin) : this(
        plugin,
        nativeRegistrar = DefaultBungeeNativeCommandRegistrar(plugin),
        { sender -> requireNotNull(BungeeAudienceAdapter(plugin).sender(sender)) },
    )

    /** Converts native invocation arguments to the portable command context. */
    override fun context(
        source: NativeCommandSender,
        alias: String,
        arguments: Array<String>,
    ): CommandContext = CommandContext(
        sender = BungeeCommandSender(audience(source)),
        arguments = arguments.toList(),
        invokedAlias = alias,
        currentInput = arguments.lastOrNull().orEmpty(),
    )

}

/** Delegates portable command replies to the native audience bridge. */
private class BungeeCommandSender(
    private val audience: AudienceSender,
) : CommandSender, AudienceSender by audience {

    override fun send(message: Component) = audience.sendMessage(message)

    override fun sendMessage(text: Component) = audience.sendMessage(text)

    override fun actionBar(text: Component) = audience.actionBar(text)

    override fun playSound(sound: Sound): Boolean = audience.playSound(sound)
}

/** Installs command and tab-completion handlers in the proxy plugin manager. */
private class DefaultBungeeNativeCommandRegistrar(
    private val plugin: Plugin,
) : NativeCommandRegistrar<NativeCommandSender> {
    override fun register(
        command: CommandDefinition,
        execute: (NativeCommandSender, Array<String>) -> Unit,
        suggest: (NativeCommandSender, Array<String>) -> java.util.concurrent.CompletionStage<List<String>>,
    ): PlatformCommandRegistration {
        val executeHandler = execute
        val suggestionHandler = suggest
        val native = object : Command(command.name, null, *command.aliases.toTypedArray()), TabExecutor {
            override fun execute(sender: NativeCommandSender, args: Array<String>) = executeHandler(sender, args)
            override fun onTabComplete(sender: NativeCommandSender, args: Array<String>): Iterable<String> =
                suggestionHandler(sender, args).toCompletableFuture().get(1, java.util.concurrent.TimeUnit.SECONDS)
        }
        plugin.proxy.pluginManager.registerCommand(plugin, native)
        return PlatformCommandRegistration { plugin.proxy.pluginManager.unregisterCommand(native) }
    }
}
