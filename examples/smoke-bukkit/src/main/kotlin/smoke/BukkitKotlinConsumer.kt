package smoke

import org.bukkit.plugin.java.JavaPlugin
import ru.privatenull.pnlibrary.api.plugin.PluginRegistration
import ru.privatenull.pnlibrary.api.runtime.PnLibrary

fun registerBukkitKotlin(library: PnLibrary, plugin: JavaPlugin): PluginRegistration =
    library.plugins.register(plugin)
