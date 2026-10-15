package smoke

import net.md_5.bungee.api.plugin.Plugin
import ru.privatenull.pnlibrary.api.plugin.PluginRegistration
import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import ru.privatenull.pnlibrary.bungee.api.BungeePlatform

fun registerBungeeKotlin(library: PnLibrary, plugin: Plugin): PluginRegistration =
    library.platforms.require(BungeePlatform::class.java).proxy.let {
        library.plugins.register(plugin)
    }
