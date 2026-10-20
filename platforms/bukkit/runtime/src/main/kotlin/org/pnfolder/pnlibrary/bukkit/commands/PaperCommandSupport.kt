package org.pnfolder.pnlibrary.bukkit.commands

import org.bukkit.command.CommandSender
import org.bukkit.plugin.Plugin
import org.pnfolder.pnlibrary.spi.commands.NativeCommandRegistrar

/**
 * Chooses how commands reach the server. On a Paper server that has the Brigadier command API and runs Java 21,
 * the Paper module registers them natively, so the client validates arguments and shows hints. Everywhere else,
 * and whenever anything about that fails, the classic registrar is used, so commands always work.
 *
 * Start the server with `-Dpnlibrary.native-commands=false` to skip the native path. Registering natively makes
 * Paper turn off Bukkit's `/reload`, which some servers may prefer to keep.
 */
internal object PaperCommandSupport {
    private const val SWITCH = "pnlibrary.native-commands"
    private const val REGISTRAR = "org.pnfolder.pnlibrary.bukkit.paper.PaperNativeCommandRegistrar"
    private const val PAPER_COMMANDS = "io.papermc.paper.command.brigadier.Commands"
    private const val PAPER_LIFECYCLE = "io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents"

    /** Returns the registrar to use: the Paper one when it can run here, otherwise [classic]. */
    fun choose(plugin: Plugin, classic: NativeCommandRegistrar<CommandSender>): NativeCommandRegistrar<CommandSender> {
        if (System.getProperty(SWITCH)?.equals("false", ignoreCase = true) == true) return classic
        if (!present(PAPER_COMMANDS, plugin) || !present(PAPER_LIFECYCLE, plugin)) return classic
        return try {
            @Suppress("UNCHECKED_CAST")
            Class.forName(REGISTRAR, true, plugin.javaClass.classLoader)
                .getConstructor(Plugin::class.java, NativeCommandRegistrar::class.java)
                .newInstance(plugin, classic) as NativeCommandRegistrar<CommandSender>
        } catch (error: Exception) {
            plugin.logger.info("Paper's command API is not used (${error.javaClass.simpleName}); using the classic command map")
            classic
        } catch (error: LinkageError) {
            // Java older than 21 cannot load the Paper module, and a different Paper API can miss a class.
            plugin.logger.info("Paper's command API is not used (${error.javaClass.simpleName}); using the classic command map")
            classic
        }
    }

    private fun present(className: String, plugin: Plugin): Boolean =
        try {
            Class.forName(className, false, plugin.javaClass.classLoader)
            true
        } catch (_: ClassNotFoundException) {
            false
        } catch (_: LinkageError) {
            false
        }
}
