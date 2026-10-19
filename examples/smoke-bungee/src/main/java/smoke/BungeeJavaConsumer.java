package smoke;

import net.md_5.bungee.api.plugin.Plugin;
import org.pnfolder.pnlibrary.api.plugin.PluginRegistration;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.bungee.api.BungeePlatform;

public final class BungeeJavaConsumer {
    public static PluginRegistration register(PnLibrary library, Plugin plugin) {
        library.getPlatforms().require(BungeePlatform.class).getProxy();
        return library.getPlugins().register(plugin);
    }
}
