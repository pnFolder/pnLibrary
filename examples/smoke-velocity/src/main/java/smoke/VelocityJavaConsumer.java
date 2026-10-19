package smoke;

import com.velocitypowered.api.plugin.PluginContainer;
import org.pnfolder.pnlibrary.api.plugin.PluginRegistration;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.velocity.api.VelocityPlatform;

public final class VelocityJavaConsumer {
    public static PluginRegistration register(PnLibrary library, PluginContainer plugin) {
        library.getPlatforms().require(VelocityPlatform.class).getProxy();
        return library.getPlugins().register(plugin);
    }
}
