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
import ru.privatenull.pnlibrary.spi.commands.PlatformCommandAdapter
import ru.privatenull.pnlibrary.spi.commands.PlatformCommandDispatcher
import ru.privatenull.pnlibrary.spi.commands.PlatformCommandRegistration
import java.lang.reflect.Constructor
import java.util.LinkedHashSet
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Thin Bukkit transport for portable command definitions. */
internal class BukkitCommandAdapter internal constructor(
    private val plugin: Plugin,
    private val registrar: BukkitNativeCommandRegistrar,
    private val audience: (NativeCommandSender) -> AudienceSender,
) : PlatformCommandAdapter {
    constructor(plugin: Plugin, audiences: BukkitAudienceService) : this(
        plugin,
        DefaultBukkitNativeCommandRegistrar(plugin),
        { sender -> requireNotNull(BukkitAudienceAdapter(audiences).sender(sender)) },
    )

    private val closed = AtomicBoolean(false)
    private val registrations = LinkedHashSet<Registration>()

    override fun register(
        owner: Any,
        command: CommandDefinition,
        dispatcher: PlatformCommandDispatcher,
    ): PlatformCommandRegistration {
        check(!closed.get()) { "Bukkit command adapter is closed" }
        val native = registrar.register(
            command,
            { sender, label, arguments ->
                dispatcher.execute(command, context(sender, label, arguments))
            },
            { sender, label, arguments ->
                runCatching {
                    dispatcher.suggest(command, context(sender, label, arguments))
                        .toCompletableFuture().get(1, TimeUnit.SECONDS)
                }.getOrDefault(emptyList())
            },
        )
        return Registration(native).also { synchronized(registrations) { registrations += it } }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val current = synchronized(registrations) { registrations.toList() }
        current.asReversed().forEach(Registration::close)
    }

    private fun context(
        sender: NativeCommandSender,
        label: String,
        arguments: Array<String>,
    ) = CommandContext(
        sender = BukkitCommandSender(sender, audience(sender)),
        arguments = arguments.toList(),
        invokedAlias = label,
        currentInput = arguments.lastOrNull().orEmpty(),
    )

    private inner class Registration(
        private val native: PlatformCommandRegistration,
    ) : PlatformCommandRegistration {
        private val registrationClosed = AtomicBoolean(false)
        override fun close() {
            if (!registrationClosed.compareAndSet(false, true)) return
            synchronized(registrations) { registrations.remove(this) }
            native.close()
        }
    }

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

internal interface BukkitNativeCommandRegistrar {
    fun register(
        command: CommandDefinition,
        execute: (NativeCommandSender, String, Array<String>) -> Unit,
        suggest: (NativeCommandSender, String, Array<String>) -> List<String>,
    ): PlatformCommandRegistration
}

private class DefaultBukkitNativeCommandRegistrar(
    private val plugin: Plugin,
) : BukkitNativeCommandRegistrar {
    override fun register(
        command: CommandDefinition,
        execute: (NativeCommandSender, String, Array<String>) -> Unit,
        suggest: (NativeCommandSender, String, Array<String>) -> List<String>,
    ): PlatformCommandRegistration {
        val executor = CommandExecutor { sender, _, label, args ->
            execute(sender, label, args)
            true
        }
        val completer = TabCompleter { sender, _, label, args -> suggest(sender, label, args) }
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

    private fun commandMap(): CommandMap {
        val field = Bukkit.getServer().javaClass.getDeclaredField("commandMap")
        field.isAccessible = true
        return field.get(Bukkit.getServer()) as CommandMap
    }

    private fun pluginCommand(name: String): PluginCommand {
        val constructor: Constructor<PluginCommand> = PluginCommand::class.java.getDeclaredConstructor(
            String::class.java,
            Plugin::class.java,
        )
        constructor.isAccessible = true
        return constructor.newInstance(name, plugin)
    }
}
