package org.pnfolder.pnlibrary.bukkit.commands

import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandMap
import org.bukkit.command.PluginCommand
import org.bukkit.command.TabCompleter
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import org.pnfolder.pnlibrary.api.audiences.AudienceSender
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.CommandSender
import org.pnfolder.pnlibrary.bukkit.BukkitAudienceAdapter
import org.pnfolder.pnlibrary.bukkit.BukkitAudienceService
import org.pnfolder.pnlibrary.spi.commands.GenericPlatformCommandAdapter
import org.pnfolder.pnlibrary.spi.commands.NativeCommandRegistrar
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandRegistration
import java.lang.reflect.Constructor
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit
import org.bukkit.command.CommandSender as NativeCommandSender

/**
 * Registers portable commands and owns their native Bukkit registrations.
 *
 * Native tab completion is synchronous, so suggestion requests use a bounded one-second wait.
 * Commands are released in reverse registration order when this adapter closes.
 */
internal class BukkitCommandAdapter internal constructor(
    private val nativeRegistrar: NativeCommandRegistrar<NativeCommandSender>,
    private val audience: (NativeCommandSender) -> AudienceSender,
    private val interactive: () -> Boolean = { true },
) : GenericPlatformCommandAdapter<NativeCommandSender>() {
    override val registrar: NativeCommandRegistrar<NativeCommandSender> = nativeRegistrar
    /** Creates the native registrar, preferring Paper's command API where the server has it, and the audience bridge. */
    constructor(audiences: BukkitAudienceService, plugin: Plugin) : this(
        nativeRegistrar = PaperCommandSupport.choose(plugin, DefaultBukkitNativeCommandRegistrar()),
        { sender -> requireNotNull(BukkitAudienceAdapter(audiences).sender(sender)) },
        { audiences.nativeTransportAvailable },
    )

    /** Converts native invocation arguments to the portable command context. */
    override fun context(
        source: NativeCommandSender,
        alias: String,
        arguments: Array<String>,
    ): CommandContext = CommandContext(
        sender = BukkitCommandSender(source, audience(source), interactive),
        arguments = arguments.toList(),
        invokedAlias = alias,
        currentInput = arguments.lastOrNull().orEmpty(),
    )

}

/** Internal wrapper; native access never crosses the public command API. */
internal class BukkitCommandSender(
    internal val native: NativeCommandSender,
    private val audience: AudienceSender,
    private val transportAvailable: () -> Boolean = { true },
) : CommandSender, AudienceSender by audience {

    /** Buttons work for players only while the Adventure transport is in use; the fallback sends plain text. */
    override val supportsInteraction: Boolean
        get() = !isConsole && transportAvailable()

    override fun send(message: Component) = audience.sendMessage(message)

    override fun sendMessage(text: Component) = audience.sendMessage(text)

    override fun actionBar(text: Component) = audience.actionBar(text)

    override fun playSound(sound: Sound): Boolean = audience.playSound(sound)
}

/** Native registration boundary used by the runtime and command tests. */
/** Uses declared plugin commands where available, otherwise the legacy command map. */
private class DefaultBukkitNativeCommandRegistrar : NativeCommandRegistrar<NativeCommandSender> {
    /** Registers portable execution and completion handlers with the native command manager. */
    override fun register(
        owner: Any,
        command: CommandDefinition,
        execute: (NativeCommandSender, String, Array<String>) -> Unit,
        suggest: (NativeCommandSender, String, Array<String>) -> CompletionStage<List<String>>,
    ): PlatformCommandRegistration {
        val ownerPlugin = owner as? Plugin
            ?: throw IllegalArgumentException("Command owner must be a Bukkit plugin")
        val executor = CommandExecutor { sender, _, label, args ->
            execute(sender, label, args)
            true
        }
        val completer = TabCompleter { sender, _, label, args ->
            suggest(sender, label, args).toCompletableFuture().get(1, TimeUnit.SECONDS)
        }
        val declared = (ownerPlugin as? JavaPlugin)?.getCommand(command.name)
        if (declared != null) {
            declared.aliases = command.aliases.toList()
            declared.executor = executor
            declared.tabCompleter = completer
            return PlatformCommandRegistration {
                declared.executor = null
                declared.tabCompleter = null
            }
        }

        val commandMap = commandMap()
        val native = pluginCommand(command.name, ownerPlugin).apply {
            aliases = command.aliases.toList()
            description = "pnLibrary portable command"
            this.executor = executor
            tabCompleter = completer
        }
        commandMap.register(ownerPlugin.name, native)
        return PlatformCommandRegistration { native.unregister(commandMap) }
    }

    /** Resolves the legacy command map used for undeclared commands. */
    private fun commandMap(): CommandMap {
        val field = Bukkit.getServer().javaClass.getDeclaredField("commandMap")
        field.isAccessible = true
        return field.get(Bukkit.getServer()) as CommandMap
    }

    /** Constructs a native plugin command through Bukkit's non-public constructor. */
    private fun pluginCommand(name: String, owner: Plugin): PluginCommand {
        val constructor: Constructor<PluginCommand> = PluginCommand::class.java.getDeclaredConstructor(
            String::class.java,
            Plugin::class.java,
        )
        constructor.isAccessible = true
        return constructor.newInstance(name, owner)
    }
}
