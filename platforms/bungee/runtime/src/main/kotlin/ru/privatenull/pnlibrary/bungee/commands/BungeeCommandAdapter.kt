package ru.privatenull.pnlibrary.bungee.commands

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.md_5.bungee.api.CommandSender as NativeCommandSender
import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.connection.ProxiedPlayer
import net.md_5.bungee.api.plugin.Command
import net.md_5.bungee.api.plugin.Plugin
import net.md_5.bungee.api.plugin.TabExecutor
import ru.privatenull.pnlibrary.api.commands.CommandContext
import ru.privatenull.pnlibrary.api.commands.CommandDefinition
import ru.privatenull.pnlibrary.api.commands.CommandSender
import ru.privatenull.pnlibrary.spi.commands.PlatformCommandAdapter
import ru.privatenull.pnlibrary.spi.commands.PlatformCommandDispatcher
import ru.privatenull.pnlibrary.spi.commands.PlatformCommandRegistration
import java.util.LinkedHashSet
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Thin BungeeCord transport for portable commands. */
internal class BungeeCommandAdapter internal constructor(
    private val plugin: Any,
    private val registrar: BungeeNativeCommandRegistrar,
    private val isConsole: (NativeCommandSender) -> Boolean,
    private val sendMessage: (NativeCommandSender, Component) -> Unit,
) : PlatformCommandAdapter {
    constructor(plugin: Plugin) : this(
        plugin,
        DefaultBungeeNativeCommandRegistrar(plugin),
        { sender -> sender === plugin.proxy.console },
        { sender, message ->
            @Suppress("DEPRECATION")
            sender.sendMessage(*TextComponent.fromLegacyText(LEGACY.serialize(message)))
        },
    )

    private val closed = AtomicBoolean(false)
    private val registrations = LinkedHashSet<Registration>()

    override fun register(
        owner: Any,
        command: CommandDefinition,
        dispatcher: PlatformCommandDispatcher,
    ): PlatformCommandRegistration {
        check(!closed.get()) { "Bungee command adapter is closed" }
        val native = registrar.register(
            command,
            { sender, arguments ->
                dispatcher.execute(command, context(sender, command.name, arguments))
            },
            { sender, arguments ->
                runCatching {
                    dispatcher.suggest(command, context(sender, command.name, arguments))
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
        alias: String,
        arguments: Array<String>,
    ) = CommandContext(
        sender = BungeeCommandSender(sender, isConsole, sendMessage),
        arguments = arguments.toList(),
        invokedAlias = alias,
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

    private companion object {
        val LEGACY: LegacyComponentSerializer = LegacyComponentSerializer.legacySection()
    }
}

private class BungeeCommandSender(
    private val native: NativeCommandSender,
    private val consoleCheck: (NativeCommandSender) -> Boolean,
    private val sendMessage: (NativeCommandSender, Component) -> Unit,
) : CommandSender {
    override val id: String = (native as? ProxiedPlayer)?.uniqueId?.toString() ?: native.name
    override val name: String get() = native.name
    override val isConsole: Boolean get() = consoleCheck(native)
    override fun hasPermission(permission: String): Boolean = native.hasPermission(permission)
    override fun send(message: Component) = sendMessage(native, message)
}

internal interface BungeeNativeCommandRegistrar {
    fun register(
        command: CommandDefinition,
        execute: (NativeCommandSender, Array<String>) -> Unit,
        suggest: (NativeCommandSender, Array<String>) -> List<String>,
    ): PlatformCommandRegistration
}

private class DefaultBungeeNativeCommandRegistrar(
    private val plugin: Plugin,
) : BungeeNativeCommandRegistrar {
    override fun register(
        command: CommandDefinition,
        execute: (NativeCommandSender, Array<String>) -> Unit,
        suggest: (NativeCommandSender, Array<String>) -> List<String>,
    ): PlatformCommandRegistration {
        val executeHandler = execute
        val suggestionHandler = suggest
        val native = object : Command(command.name, null, *command.aliases.toTypedArray()), TabExecutor {
            override fun execute(sender: NativeCommandSender, args: Array<String>) = executeHandler(sender, args)
            override fun onTabComplete(sender: NativeCommandSender, args: Array<String>): Iterable<String> =
                suggestionHandler(sender, args)
        }
        plugin.proxy.pluginManager.registerCommand(plugin, native)
        return PlatformCommandRegistration { plugin.proxy.pluginManager.unregisterCommand(native) }
    }
}
