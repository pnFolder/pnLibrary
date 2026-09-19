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
    private val audience: (NativeCommandSender) -> AudienceSender,
) : PlatformCommandAdapter {
    constructor(plugin: Plugin) : this(
        plugin,
        DefaultBungeeNativeCommandRegistrar(plugin),
        { sender -> requireNotNull(BungeeAudienceAdapter(plugin).sender(sender)) },
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
        sender = BungeeCommandSender(audience(sender)),
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

}

private class BungeeCommandSender(
    private val audience: AudienceSender,
) : CommandSender, AudienceSender by audience {
    override fun send(message: Component) = audience.sendMessage(message)
    override fun sendMessage(text: Component) = audience.sendMessage(text)
    override fun actionBar(text: Component) = audience.actionBar(text)
    override fun playSound(sound: Sound): Boolean = audience.playSound(sound)
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
