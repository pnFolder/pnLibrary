package org.pnfolder.pnlibrary.bukkit.commands

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.audience.ForwardingAudience
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandMap
import org.bukkit.command.PluginCommand
import org.bukkit.command.TabCompleter
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.CommandSender
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition
import org.pnfolder.pnlibrary.api.logging.PnLogger
import org.pnfolder.pnlibrary.api.audience.isConsole
import org.pnfolder.pnlibrary.bukkit.PnLoggerBuffer
import org.pnfolder.pnlibrary.bukkit.audience.BukkitAudienceAdapter
import org.pnfolder.pnlibrary.bukkit.audience.BukkitAdventureTransport
import org.pnfolder.pnlibrary.core.commands.engine.MAIN_THREAD_SUGGESTION_MILLIS
import org.pnfolder.pnlibrary.core.commands.engine.suggestionsWithin
import org.pnfolder.pnlibrary.spi.commands.GenericPlatformCommandAdapter
import org.pnfolder.pnlibrary.spi.commands.NativeCommandRegistrar
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandRegistration
import java.lang.reflect.Constructor
import java.util.concurrent.CompletionStage
import org.bukkit.command.CommandSender as NativeCommandSender

internal class BukkitCommandAdapter internal constructor(
    private val nativeRegistrar: NativeCommandRegistrar<NativeCommandSender>,
    private val audience: (NativeCommandSender) -> Audience,
    private val interactive: () -> Boolean = { true },
) : GenericPlatformCommandAdapter<NativeCommandSender>() {
    override val registrar: NativeCommandRegistrar<NativeCommandSender> = nativeRegistrar
    constructor(audiences: BukkitAdventureTransport, plugin: Plugin, pnLogger: () -> PnLogger, logBuffer: PnLoggerBuffer) : this(
        nativeRegistrar = PaperCommandSupport.choose(plugin, DefaultBukkitNativeCommandRegistrar(), pnLogger, logBuffer),
        { sender -> requireNotNull(BukkitAudienceAdapter(audiences).of(sender)) },
        { audiences.nativeTransportAvailable },
    )

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

internal class BukkitCommandSender(
    internal val native: NativeCommandSender,
    private val audience: Audience,
    private val transportAvailable: () -> Boolean = { true },
) : CommandSender, ForwardingAudience.Single {
    override fun audience(): Audience = audience

    // Buttons need the Adventure transport; without it the sender receives plain text and can't click anything.
    override val supportsInteraction: Boolean
        get() = !isConsole && transportAvailable()
}

private class DefaultBukkitNativeCommandRegistrar : NativeCommandRegistrar<NativeCommandSender> {
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
            suggest(sender, label, args).suggestionsWithin(MAIN_THREAD_SUGGESTION_MILLIS)
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

    private fun commandMap(): CommandMap {
        val field = Bukkit.getServer().javaClass.getDeclaredField("commandMap")
        field.isAccessible = true
        return field.get(Bukkit.getServer()) as CommandMap
    }

    private fun pluginCommand(name: String, owner: Plugin): PluginCommand {
        val constructor: Constructor<PluginCommand> = PluginCommand::class.java.getDeclaredConstructor(
            String::class.java,
            Plugin::class.java,
        )
        constructor.isAccessible = true
        return constructor.newInstance(name, owner)
    }
}
