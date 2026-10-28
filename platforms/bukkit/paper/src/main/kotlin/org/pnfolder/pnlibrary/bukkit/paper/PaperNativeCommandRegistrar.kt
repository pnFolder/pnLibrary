package org.pnfolder.pnlibrary.bukkit.paper

import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.handler.LifecycleEventHandler
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.command.CommandSender
import org.bukkit.plugin.Plugin
import org.pnfolder.pnlibrary.api.commands.CommandDefinition
import org.pnfolder.pnlibrary.spi.commands.NativeCommandRegistrar
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandRegistration
import java.util.concurrent.CompletionStage
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Registers portable commands through Paper's Brigadier command API, falling back to [fallback], the classic
 * command map, for anything the API cannot take.
 *
 * Paper builds its command tree once, when the server has loaded its plugins, and again on a datapack reload;
 * plugins may add commands only while that happens. So commands registered before then are collected and
 * registered natively in that event, and every later registration, or a command Paper refuses, goes through
 * [fallback] and still works, only without client-side validation.
 *
 * Registering a handler for this event makes Paper turn off Bukkit's `/reload`; that is Paper's rule, not ours.
 */
class PaperNativeCommandRegistrar(
    private val plugin: Plugin,
    private val fallback: NativeCommandRegistrar<CommandSender>,
) : NativeCommandRegistrar<CommandSender> {
    private class Entry(
        val owner: Plugin?,
        val command: CommandDefinition,
        val execute: (CommandSender, String, Array<String>) -> Unit,
        val suggest: (CommandSender, String, Array<String>) -> CompletionStage<List<String>>,
    ) {
        @Volatile var active = true
        @Volatile var fallbackRegistration: PlatformCommandRegistration? = null
    }

    private val entries = CopyOnWriteArrayList<Entry>()

    @Volatile
    private var committed = false

    init {
        plugin.lifecycleManager.registerEventHandler(
            LifecycleEvents.COMMANDS,
            LifecycleEventHandler<ReloadableRegistrarEvent<Commands>> { event ->
                committed = true
                val registrar = event.registrar()
                entries.filter { it.active }.forEach { registerNative(registrar, it) }
            },
        )
    }

    /** Registers [command] natively through Paper's Brigadier API on behalf of [owner]. */
    override fun register(
        owner: Any,
        command: CommandDefinition,
        execute: (CommandSender, String, Array<String>) -> Unit,
        suggest: (CommandSender, String, Array<String>) -> CompletionStage<List<String>>,
    ): PlatformCommandRegistration {
        if (committed) return fallback.register(owner, command, execute, suggest)
        val entry = Entry(owner as? Plugin, command, execute, suggest)
        entries += entry
        return PlatformCommandRegistration {
            // Paper cannot remove a command from its tree; the command simply stops answering until the next rebuild.
            entry.active = false
            entries -= entry
            entry.fallbackRegistration?.close()
        }
    }

    private fun registerNative(registrar: Commands, entry: Entry) {
        try {
            val node = BrigadierTrees.build(entry.command, invocationOf(entry))
            val meta = (entry.owner ?: plugin).pluginMeta
            registrar.register(meta, node, "pnLibrary portable command", entry.command.aliases.toList())
        } catch (error: Exception) {
            plugin.logger.warning(
                "Command /${entry.command.name} could not use Paper's command API " +
                    "(${error.javaClass.simpleName}); using the classic command map",
            )
            entry.fallbackRegistration = fallback.register(entry.owner ?: plugin, entry.command, entry.execute, entry.suggest)
        }
    }

    private fun invocationOf(entry: Entry) = object : NativeInvocation<CommandSourceStack> {
        override fun execute(source: CommandSourceStack, input: String) {
            if (!entry.active) {
                source.sender.sendMessage("Команда сейчас недоступна.")
                return
            }
            val line = CommandLine.of(input, keepTrailing = false)
            entry.execute(source.sender, line.label, line.arguments.toTypedArray())
        }

        override fun suggest(source: CommandSourceStack, input: String): CompletionStage<List<String>> {
            val line = CommandLine.of(input, keepTrailing = true)
            return entry.suggest(source.sender, line.label, line.arguments.toTypedArray())
        }
    }
}
