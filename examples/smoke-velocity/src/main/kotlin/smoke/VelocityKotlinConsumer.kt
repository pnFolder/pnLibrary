package smoke

import com.velocitypowered.api.plugin.PluginContainer
import org.pnfolder.pnlibrary.api.plugin.PluginRegistration
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.velocity.api.VelocityPlatform

fun registerVelocityKotlin(library: PnLibrary, plugin: PluginContainer): PluginRegistration =
    library.platforms.require(VelocityPlatform::class.java).proxy.let {
        library.plugins.register(plugin)
    }
