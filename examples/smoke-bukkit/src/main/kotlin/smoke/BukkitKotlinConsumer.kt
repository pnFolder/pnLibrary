package smoke

import org.bukkit.plugin.java.JavaPlugin
import org.pnfolder.pnlibrary.api.plugin.PluginRegistration
import org.pnfolder.pnlibrary.api.runtime.PnLibrary

fun registerBukkitKotlin(library: PnLibrary, plugin: JavaPlugin): PluginRegistration =
    library.plugins.register(plugin)
