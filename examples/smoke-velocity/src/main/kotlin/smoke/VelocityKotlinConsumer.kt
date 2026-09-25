package smoke

import com.velocitypowered.api.plugin.PluginContainer
import ru.privatenull.pnlibrary.api.plugin.PluginRegistration
import ru.privatenull.pnlibrary.api.runtime.PnLibrary

fun registerVelocityKotlin(library: PnLibrary, plugin: PluginContainer): PluginRegistration =
    library.plugins.register(plugin)
