package smoke;

import net.md_5.bungee.api.plugin.Plugin;
import ru.privatenull.pnlibrary.api.plugin.PluginRegistration;
import ru.privatenull.pnlibrary.api.runtime.PnLibrary;

public final class BungeeJavaConsumer {
    public static PluginRegistration register(PnLibrary library, Plugin plugin) {
        return library.getPlugins().register(plugin);
    }
}
