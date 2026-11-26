package org.pnfolder.pnlibrary.bukkit.commands;

import java.util.function.Supplier;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.pnfolder.pnlibrary.api.logging.LogLevel;
import org.pnfolder.pnlibrary.api.logging.PnLogger;
import org.pnfolder.pnlibrary.bukkit.PnLoggerBuffer;
import org.pnfolder.pnlibrary.spi.commands.NativeCommandRegistrar;

// -Dpnlibrary.native-commands=false skips the Paper path.
// Registering natively makes Paper disable Bukkit's /reload; servers that rely on it should use this flag.
final class PaperCommandSupport {
    private static final String SWITCH = "pnlibrary.native-commands";
    private static final String REGISTRAR = "org.pnfolder.pnlibrary.bukkit.paper.PaperNativeCommandRegistrar";
    private static final String PAPER_COMMANDS = "io.papermc.paper.command.brigadier.Commands";
    private static final String PAPER_LIFECYCLE = "io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents";

    private PaperCommandSupport() {
    }

    @SuppressWarnings("unchecked")
    static NativeCommandRegistrar<CommandSender> choose(Plugin plugin, NativeCommandRegistrar<CommandSender> classic,
                                                        Supplier<PnLogger> pnLogger, PnLoggerBuffer logBuffer) {
        if ("false".equalsIgnoreCase(System.getProperty(SWITCH))) {
            return classic;
        }
        if (!present(PAPER_COMMANDS, plugin) || !present(PAPER_LIFECYCLE, plugin)) {
            return classic;
        }
        try {
            return (NativeCommandRegistrar<CommandSender>) Class.forName(REGISTRAR, true, plugin.getClass().getClassLoader())
                .getConstructor(Plugin.class, NativeCommandRegistrar.class, Supplier.class)
                .newInstance(plugin, classic, pnLogger);
        } catch (Exception | LinkageError error) {
            // Java older than 21 cannot load the Paper module, and a different Paper API can miss a class.
            logBuffer.log(LogLevel.INFO, "Paper's command API is not used (" + error.getClass().getSimpleName()
                + "); using the classic command map");
            return classic;
        }
    }

    private static boolean present(String className, Plugin plugin) {
        try {
            Class.forName(className, false, plugin.getClass().getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError error) {
            return false;
        }
    }
}
