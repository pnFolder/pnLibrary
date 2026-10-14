package smoke;

import com.velocitypowered.api.plugin.PluginContainer;
import ru.privatenull.pnlibrary.api.plugin.PluginRegistration;
import ru.privatenull.pnlibrary.api.runtime.PnLibrary;
import ru.privatenull.pnlibrary.velocity.api.VelocityPlatform;

public final class VelocityJavaConsumer {
    public static PluginRegistration register(PnLibrary library, PluginContainer plugin) {
        library.getPlatforms().require(VelocityPlatform.class).getProxy();
        return library.getPlugins().register(plugin);
    }
}
