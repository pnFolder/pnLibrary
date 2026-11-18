package org.pnfolder.pnlibrary.bukkit.paper

import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.handler.LifecycleEventHandler
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.plugin.Plugin
import org.pnfolder.pnlibrary.api.commands.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.CommandNode
import org.pnfolder.pnlibrary.api.commands.SenderRule
import org.pnfolder.pnlibrary.api.logging.PnLogger
import org.pnfolder.pnlibrary.spi.commands.NativeCommandRegistrar
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandRegistration
import java.util.concurrent.CompletionStage
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

// Paper builds its command tree on plugin load and on datapack reload — commands can only be added then.
// Everything registered before that event is queued here and submitted in bulk; anything later goes through
// [fallback] (classic command map) and still works, only without client-side validation.
// Registering a lifecycle handler also disables Bukkit's /reload — that is Paper's rule, not ours.
@Suppress("UnstableApiUsage")
class PaperNativeCommandRegistrar(
    private val plugin: Plugin,
    private val fallback: NativeCommandRegistrar<CommandSender>,
    private val pnLogger: () -> PnLogger,
) : NativeCommandRegistrar<CommandSender> {
    private class Entry(
        val owner: Plugin?,
        val command: CommandDefinition,
        val execute: (CommandSender, String, Array<String>) -> Unit,
        val suggest: (CommandSender, String, Array<String>) -> CompletionStage<List<String>>,
    ) {
        @Volatile var active = true
        @Volatile var fallbackRegistration: PlatformCommandRegistration? = null
        val suggestionFailureReported = AtomicBoolean(false)
    }

    private val entries = CopyOnWriteArrayList<Entry>()

    @Volatile
    private var committed = false

    init {
        plugin.lifecycleManager.registerEventHandler(
            LifecycleEvents.COMMANDS,
            LifecycleEventHandler { event: ReloadableRegistrarEvent<Commands> ->
                committed = true
                val registrar = event.registrar()
                entries.filter { it.active }.forEach { registerNative(registrar, it) }
            },
        )
    }

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
            pnLogger().warning("Command /${entry.command.name} could not use Paper's command API; using the classic command map", error)
            entry.fallbackRegistration = fallback.register(entry.owner ?: plugin, entry.command, entry.execute, entry.suggest)
        }
    }

    private fun invocationOf(entry: Entry) = object : NativeInvocation<CommandSourceStack> {
        override fun execute(source: CommandSourceStack, input: String) {
            if (!entry.active) {
                source.sender.sendMessage("This command is currently unavailable.")
                return
            }
            val line = CommandLine.of(input, keepTrailing = false)
            entry.execute(source.sender, line.label, line.arguments.toTypedArray())
        }

        override fun allowed(source: CommandSourceStack, node: CommandNode): Boolean {
            val sender = source.sender
            val console = sender is ConsoleCommandSender
            val permission = node.permission
            val permitted = permission == null ||
                (console && node.consoleBypassesPermission) ||
                sender.hasPermission(permission) ||
                node.alsoPermissions.any { sender.hasPermission(it) }
            val senderFits = when (node.senderRule) {
                SenderRule.PLAYER -> !console
                SenderRule.CONSOLE -> console
                SenderRule.ANY -> true
            }
            return permitted && senderFits
        }

        override fun suggest(source: CommandSourceStack, input: String): CompletionStage<List<String>> {
            val line = CommandLine.of(input, keepTrailing = true)
            return entry.suggest(source.sender, line.label, line.arguments.toTypedArray()).whenComplete { _, error ->
                // Tab runs on every keystroke, so one report per command is enough.
                if (error != null && entry.suggestionFailureReported.compareAndSet(false, true)) {
                    pnLogger().error("Tab completion of /${entry.command.name} failed", error)
                }
            }
        }
    }
}
