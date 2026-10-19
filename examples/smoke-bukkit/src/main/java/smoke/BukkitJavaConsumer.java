package smoke;

import org.bukkit.plugin.java.JavaPlugin;
import org.pnfolder.pnlibrary.api.plugin.PluginRegistration;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;

public final class BukkitJavaConsumer {
    public static PluginRegistration register(PnLibrary library, JavaPlugin plugin) {
        return library.getPlugins().register(plugin);
    }
}
