package smoke;

import com.velocitypowered.api.plugin.PluginContainer;
import ru.privatenull.pnlibrary.api.plugin.PluginRegistration;
import ru.privatenull.pnlibrary.api.runtime.PnLibrary;

public final class VelocityJavaConsumer {
    public static PluginRegistration register(PnLibrary library, PluginContainer plugin) {
        return library.getPlugins().register(plugin);
    }
}
