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

/**
 * Registers portable commands with BungeeCord and owns their native registrations.
 *
 * Native tab completion is synchronous, so suggestions have a bounded one-second wait.
 * Closing this adapter unregisters commands in reverse registration order.
 */
internal class BungeeCommandAdapter internal constructor(
    private val plugin: Any,
    private val registrar: BungeeNativeCommandRegistrar,
    private val audience: (NativeCommandSender) -> AudienceSender,
) : PlatformCommandAdapter {
    /** Creates the native command registrar and audience bridge. */
    constructor(plugin: Plugin) : this(
        plugin,
        DefaultBungeeNativeCommandRegistrar(plugin),
        { sender -> requireNotNull(BungeeAudienceAdapter(plugin).sender(sender)) },
    )

    private val closed = AtomicBoolean(false)
    private val registrations = LinkedHashSet<Registration>()

    /** Registers execution and tab-completion callbacks with the native command manager. */
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
        val registration = Registration(native)
        synchronized(registrations) {
            registrations += registration
        }
        return registration
    }

    /** Unregisters owned commands once. */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val current = synchronized(registrations) { registrations.toList() }
        current.asReversed().forEach(Registration::close)
    }

    /** Converts native invocation arguments to the portable command context. */
    private fun context(
        sender: NativeCommandSender,
        alias: String,
        arguments: Array<String>,
    ): CommandContext = CommandContext(
        sender = BungeeCommandSender(audience(sender)),
        arguments = arguments.toList(),
        invokedAlias = alias,
        currentInput = arguments.lastOrNull().orEmpty(),
    )

    /** Tracks one native command registration with idempotent cleanup. */
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

/** Delegates portable command replies to the native audience bridge. */
private class BungeeCommandSender(
    private val audience: AudienceSender,
) : CommandSender, AudienceSender by audience {

    override fun send(message: Component) = audience.sendMessage(message)

    override fun sendMessage(text: Component) = audience.sendMessage(text)

    override fun actionBar(text: Component) = audience.actionBar(text)

    override fun playSound(sound: Sound): Boolean = audience.playSound(sound)
}

/** Native registration boundary used by the runtime and command tests. */
internal interface BungeeNativeCommandRegistrar {
    /** Registers handlers and returns an unregister operation. */
    fun register(
        command: CommandDefinition,
        execute: (NativeCommandSender, Array<String>) -> Unit,
        suggest: (NativeCommandSender, Array<String>) -> List<String>,
    ): PlatformCommandRegistration
}

/** Installs command and tab-completion handlers in the proxy plugin manager. */
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
