package org.pnfolder.pnlibrary.bukkit.commands

import org.bukkit.command.CommandSender
import org.bukkit.plugin.Plugin
import org.pnfolder.pnlibrary.api.logging.LogLevel
import org.pnfolder.pnlibrary.api.logging.PnLogger
import org.pnfolder.pnlibrary.bukkit.PnLoggerBuffer
import org.pnfolder.pnlibrary.spi.commands.NativeCommandRegistrar

// -Dpnlibrary.native-commands=false skips the Paper path.
// Registering natively makes Paper disable Bukkit's /reload — servers that rely on it should use this flag.
internal object PaperCommandSupport {
    private const val SWITCH = "pnlibrary.native-commands"
    private const val REGISTRAR = "org.pnfolder.pnlibrary.bukkit.paper.PaperNativeCommandRegistrar"
    private const val PAPER_COMMANDS = "io.papermc.paper.command.brigadier.Commands"
    private const val PAPER_LIFECYCLE = "io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents"

    fun choose(plugin: Plugin, classic: NativeCommandRegistrar<CommandSender>, pnLogger: () -> PnLogger, logBuffer: PnLoggerBuffer): NativeCommandRegistrar<CommandSender> {
        if (System.getProperty(SWITCH)?.equals("false", ignoreCase = true) == true) return classic
        if (!present(PAPER_COMMANDS, plugin) || !present(PAPER_LIFECYCLE, plugin)) return classic
        return try {
            @Suppress("UNCHECKED_CAST")
            Class.forName(REGISTRAR, true, plugin.javaClass.classLoader)
                .getConstructor(Plugin::class.java, NativeCommandRegistrar::class.java, Function0::class.java)
                .newInstance(plugin, classic, pnLogger) as NativeCommandRegistrar<CommandSender>
        } catch (error: Exception) {
            logBuffer.log(LogLevel.INFO, "Paper's command API is not used (${error.javaClass.simpleName}); using the classic command map")
            classic
        } catch (error: LinkageError) {
            // Java older than 21 cannot load the Paper module, and a different Paper API can miss a class.
            logBuffer.log(LogLevel.INFO, "Paper's command API is not used (${error.javaClass.simpleName}); using the classic command map")
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
