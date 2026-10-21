package org.pnfolder.pnlibrary.bukkit.bootstrap;

import org.bukkit.ChatColor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.pnfolder.pnlibrary.bootstrap.BootstrapConsole;
import org.pnfolder.pnlibrary.bootstrap.BootstrapOptions;
import org.pnfolder.pnlibrary.bootstrap.BootstrapPlatform;
import org.pnfolder.pnlibrary.bootstrap.LibraryInstaller;
import org.pnfolder.pnlibrary.console.ConsoleTheme;

/**
 * Makes sure a suitable pnLibrary runs before the embedding Bukkit plugin uses its API. Shade it into the plugin; it
 * has no dependency on pnLibrary itself.
 *
 * <pre>{@code
 * public void onLoad() {
 *     if (!PnLibraryBootstrap.ensureInstalled(this, "1.0.0")) getServer().getPluginManager().disablePlugin(this);
 * }
 * }</pre>
 *
 * Bukkit can load a plugin while running, so a missing pnLibrary is downloaded and started at once.
 */
public final class PnLibraryBootstrap {
    private PnLibraryBootstrap() {}

    /**
     * Installs pnLibrary from the default repository when it is missing, or checks the installed version.
     *
     * @param plugin plugin that needs pnLibrary
     * @param minimumVersion lowest pnLibrary version the plugin works with
     * @return {@code true} when a suitable pnLibrary is running
     */
    public static boolean ensureInstalled(JavaPlugin plugin, String minimumVersion) {
        return ensureInstalled(plugin, BootstrapOptions.builder(minimumVersion).build());
    }

    /**
     * Installs pnLibrary according to {@code options} when it is missing, or checks the installed version.
     *
     * @param plugin plugin that needs pnLibrary
     * @param options minimum version, repository and size limit
     * @return {@code true} when a suitable pnLibrary is running
     */
    public static boolean ensureInstalled(JavaPlugin plugin, BootstrapOptions options) {
        LibraryInstaller installer = installer(plugin, options);
        Plugin installed = plugin.getServer().getPluginManager().getPlugin(BootstrapPlatform.BUKKIT.pluginName());
        if (installed != null) return installer.checkInstalled(versionOf(installed));
        LibraryInstaller.Placement placement = null;
        try {
            placement = installer.download(plugin.getDataFolder().getParentFile().toPath());
            Plugin loaded = plugin.getServer().getPluginManager().loadPlugin(placement.path().toFile());
            if (loaded == null || !BootstrapPlatform.BUKKIT.pluginName().equals(loaded.getName())) {
                throw new IllegalStateException("downloaded JAR is not the pnLibrary Bukkit plugin");
            }
            plugin.getServer().getPluginManager().enablePlugin(loaded);
            if (!loaded.isEnabled()) throw new IllegalStateException("pnLibrary was installed but failed to enable");
            placement.commit();
            installer.installed(placement, false);
            return installer.checkInstalled(versionOf(loaded));
        } catch (Throwable error) {
            if (placement != null) {
                try {
                    placement.rollback();
                } catch (Exception rollbackError) {
                    error.addSuppressed(rollbackError);
                }
            }
            installer.failed(error);
            return false;
        }
    }

    /**
     * Checks that pnLibrary runs and is at least {@code minimumVersion}, without installing anything.
     *
     * @param plugin plugin that needs pnLibrary
     * @param minimumVersion lowest pnLibrary version the plugin works with
     * @return {@code true} when a suitable pnLibrary is running
     */
    public static boolean ensureCompatible(JavaPlugin plugin, String minimumVersion) {
        LibraryInstaller installer = installer(plugin, BootstrapOptions.builder(minimumVersion).build());
        Plugin installed = plugin.getServer().getPluginManager().getPlugin(BootstrapPlatform.BUKKIT.pluginName());
        if (installed == null) {
            installer.failed(new IllegalStateException("pnLibrary не установлена"));
            return false;
        }
        return installer.checkInstalled(versionOf(installed));
    }

    private static String versionOf(Plugin library) {
        return library.isEnabled() ? library.getDescription().getVersion() : null;
    }

    private static LibraryInstaller installer(JavaPlugin plugin, BootstrapOptions options) {
        BootstrapConsole console = new BootstrapConsole() {
            @Override
            public ConsoleTheme theme(Tone tone) {
                switch (tone) {
                    case SUCCESS: return PnLibraryBootstrap.theme(ChatColor.DARK_GREEN, ChatColor.GREEN);
                    case FAILURE: return PnLibraryBootstrap.theme(ChatColor.DARK_RED, ChatColor.RED);
                    default: return PnLibraryBootstrap.theme(ChatColor.GOLD, ChatColor.YELLOW);
                }
            }

            @Override
            public void send(String line) {
                plugin.getServer().getConsoleSender().sendMessage(line);
            }
        };
        return new LibraryInstaller(BootstrapPlatform.BUKKIT, options, plugin.getName(), console);
    }

    private static ConsoleTheme theme(ChatColor border, ChatColor accent) {
        return new ConsoleTheme(border.toString(), accent.toString(), ChatColor.WHITE.toString(),
                ChatColor.GRAY.toString(), ChatColor.RESET.toString());
    }
}
