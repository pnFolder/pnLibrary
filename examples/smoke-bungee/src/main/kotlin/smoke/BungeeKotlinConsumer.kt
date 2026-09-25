package smoke

import net.md_5.bungee.api.plugin.Plugin
import ru.privatenull.pnlibrary.api.plugin.PluginRegistration
import ru.privatenull.pnlibrary.api.runtime.PnLibrary

fun registerBungeeKotlin(library: PnLibrary, plugin: Plugin): PluginRegistration =
    library.plugins.register(plugin)
