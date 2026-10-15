package smoke

import com.velocitypowered.api.plugin.PluginContainer
import ru.privatenull.pnlibrary.api.plugin.PluginRegistration
import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import ru.privatenull.pnlibrary.velocity.api.VelocityPlatform

fun registerVelocityKotlin(library: PnLibrary, plugin: PluginContainer): PluginRegistration =
    library.platforms.require(VelocityPlatform::class.java).proxy.let {
        library.plugins.register(plugin)
    }
