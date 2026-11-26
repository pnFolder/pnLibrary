package org.pnfolder.pnlibrary.bukkit;

import java.util.Arrays;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.pnfolder.pnlibrary.spi.platform.PlatformPlugins;
import org.pnfolder.pnlibrary.spi.platform.PluginSnapshot;

/** The installed Bukkit plugins as the shared platform contract sees them. */
final class BukkitPlugins {
    private BukkitPlugins() {
    }

    static PlatformPlugins<Plugin> create() {
        return new PlatformPlugins<>("Bukkit",
            () -> Arrays.asList(Bukkit.getPluginManager().getPlugins()),
            owner -> owner instanceof Plugin ? (Plugin) owner : null,
            installed -> {
                PluginDescriptionFile description = installed.getDescription();
                return new PluginSnapshot(installed.getName(), installed.getName(), description.getVersion(),
                    description.getAuthors(), description.getMain());
            });
    }
}
