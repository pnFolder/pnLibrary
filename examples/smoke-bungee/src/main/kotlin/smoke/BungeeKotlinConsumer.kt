package smoke

import net.md_5.bungee.api.plugin.Plugin
import org.pnfolder.pnlibrary.api.plugin.PluginRegistration
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.bungee.api.BungeePlatform

fun registerBungeeKotlin(library: PnLibrary, plugin: Plugin): PluginRegistration =
    library.platforms.require(BungeePlatform::class.java).proxy.let {
        library.plugins.register(plugin)
    }
