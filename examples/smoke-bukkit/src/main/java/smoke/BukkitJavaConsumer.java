package smoke;

import org.bukkit.plugin.java.JavaPlugin;
import ru.privatenull.pnlibrary.api.plugin.PluginRegistration;
import ru.privatenull.pnlibrary.api.runtime.PnLibrary;

public final class BukkitJavaConsumer {
    public static PluginRegistration register(PnLibrary library, JavaPlugin plugin) {
        return library.getPlugins().register(plugin);
    }
}
