package ru.privatenull.pnlibrary.bukkit.commands

import net.kyori.adventure.text.Component
import net.kyori.adventure.sound.Sound
import org.bukkit.Bukkit
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandMap
import org.bukkit.command.CommandSender as NativeCommandSender
import org.bukkit.command.PluginCommand
import org.bukkit.command.TabCompleter
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import ru.privatenull.pnlibrary.api.commands.CommandContext
import ru.privatenull.pnlibrary.api.commands.CommandDefinition
import ru.privatenull.pnlibrary.api.commands.CommandSender
import ru.privatenull.pnlibrary.api.audiences.AudienceSender
import ru.privatenull.pnlibrary.bukkit.BukkitAudienceAdapter
import ru.privatenull.pnlibrary.bukkit.BukkitAudienceService
import ru.privatenull.pnlibrary.spi.commands.PlatformCommandRegistration
import ru.privatenull.pnlibrary.spi.commands.GenericPlatformCommandAdapter
import ru.privatenull.pnlibrary.spi.commands.NativeCommandRegistrar
import java.lang.reflect.Constructor

/**
 * Registers portable commands and owns their native Bukkit registrations.
 *
 * Native tab completion is synchronous, so suggestion requests use a bounded one-second wait.
 * Commands are released in reverse registration order when this adapter closes.
 */
internal class BukkitCommandAdapter internal constructor(
    private val plugin: Plugin,
    private val nativeRegistrar: NativeCommandRegistrar<NativeCommandSender>,
    private val audience: (NativeCommandSender) -> AudienceSender,
) : GenericPlatformCommandAdapter<NativeCommandSender>() {
    override val registrar: NativeCommandRegistrar<NativeCommandSender> = nativeRegistrar
    /** Creates the native registrar and audience bridge. */
    constructor(plugin: Plugin, audiences: BukkitAudienceService) : this(
        plugin,
        nativeRegistrar = DefaultBukkitNativeCommandRegistrar(plugin),
        { sender -> requireNotNull(BukkitAudienceAdapter(audiences).sender(sender)) },
    )

    /** Converts native invocation arguments to the portable command context. */
    override fun context(
        source: NativeCommandSender,
        alias: String,
        arguments: Array<String>,
    ): CommandContext = CommandContext(
        sender = BukkitCommandSender(source, audience(source)),
        arguments = arguments.toList(),
        invokedAlias = alias,
        currentInput = arguments.lastOrNull().orEmpty(),
    )

}

/** Internal wrapper; native access never crosses the public command API. */
internal class BukkitCommandSender(
    internal val native: NativeCommandSender,
    private val audience: AudienceSender,
) : CommandSender, AudienceSender by audience {

    override fun send(message: Component) = audience.sendMessage(message)

    override fun sendMessage(text: Component) = audience.sendMessage(text)

    override fun actionBar(text: Component) = audience.actionBar(text)

    override fun playSound(sound: Sound): Boolean = audience.playSound(sound)
}

/** Native registration boundary used by the runtime and command tests. */
/** Uses declared plugin commands where available, otherwise the legacy command map. */
private class DefaultBukkitNativeCommandRegistrar(
    private val plugin: Plugin,
) : NativeCommandRegistrar<NativeCommandSender> {
    /** Registers portable execution and completion handlers with the native command manager. */
    override fun register(
        command: CommandDefinition,
        execute: (NativeCommandSender, Array<String>) -> Unit,
        suggest: (NativeCommandSender, Array<String>) -> java.util.concurrent.CompletionStage<List<String>>,
    ): PlatformCommandRegistration {
        val executor = CommandExecutor { sender, _, _, args ->
            execute(sender, args)
            true
        }
        val completer = TabCompleter { sender, _, _, args ->
            suggest(sender, args).toCompletableFuture().get(1, java.util.concurrent.TimeUnit.SECONDS)
        }
        val declared = (plugin as? JavaPlugin)?.getCommand(command.name)
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
        val native = pluginCommand(command.name).apply {
            aliases = command.aliases.toList()
            description = "pnLibrary portable command"
            this.executor = executor
            tabCompleter = completer
        }
        commandMap.register(plugin.name, native)
        return PlatformCommandRegistration { native.unregister(commandMap) }
    }

    /** Resolves the legacy command map used for undeclared commands. */
    private fun commandMap(): CommandMap {
        val field = Bukkit.getServer().javaClass.getDeclaredField("commandMap")
        field.isAccessible = true
        return field.get(Bukkit.getServer()) as CommandMap
    }

    /** Constructs a native plugin command through Bukkit's non-public constructor. */
    private fun pluginCommand(name: String): PluginCommand {
        val constructor: Constructor<PluginCommand> = PluginCommand::class.java.getDeclaredConstructor(
            String::class.java,
            Plugin::class.java,
        )
        constructor.isAccessible = true
        return constructor.newInstance(name, plugin)
    }
}
