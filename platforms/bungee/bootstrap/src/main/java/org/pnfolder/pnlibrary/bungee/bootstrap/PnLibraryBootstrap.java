package org.pnfolder.pnlibrary.bungee.bootstrap;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import org.pnfolder.pnlibrary.bootstrap.BootstrapConsole;
import org.pnfolder.pnlibrary.bootstrap.BootstrapOptions;
import org.pnfolder.pnlibrary.bootstrap.BootstrapPlatform;
import org.pnfolder.pnlibrary.bootstrap.LibraryInstaller;
import org.pnfolder.pnlibrary.console.ConsoleTheme;

import java.util.concurrent.TimeUnit;

/**
 * Makes sure a suitable pnLibrary runs before the embedding BungeeCord plugin uses its API. Shade it into the plugin;
 * it has no dependency on pnLibrary itself.
 *
 * <pre>{@code
 * public void onLoad() {
 *     ready = PnLibraryBootstrap.ensureInstalled(this, "1.0.0");
 * }
 * public void onEnable() {
 *     if (!ready) return;   // pnLibrary is missing or too old; the console says why
 *     ...
 * }
 * }</pre>
 *
 * BungeeCord cannot load a plugin while running: a missing pnLibrary is downloaded into {@code plugins}, and the
 * plugin starts after the next proxy restart. With {@link BootstrapOptions.Builder#restartCountdown(int)} the proxy
 * counts down and stops by itself; otherwise the console shows the command to stop it.
 */
public final class PnLibraryBootstrap {
    private PnLibraryBootstrap() {}

    /**
     * Downloads pnLibrary from the default repository when it is missing, or checks the installed version.
     *
     * @param plugin plugin that needs pnLibrary
     * @param minimumVersion lowest pnLibrary version the plugin works with
     * @return {@code true} when a suitable pnLibrary is loaded; {@code false} after a download that needs a restart
     */
    public static boolean ensureInstalled(Plugin plugin, String minimumVersion) {
        return ensureInstalled(plugin, BootstrapOptions.builder(minimumVersion).build());
    }

    /**
     * Downloads pnLibrary according to {@code options} when it is missing, or checks the installed version.
     *
     * @param plugin plugin that needs pnLibrary
     * @param options minimum version, repository and size limit
     * @return {@code true} when a suitable pnLibrary is loaded; {@code false} after a download that needs a restart
     */
    public static boolean ensureInstalled(Plugin plugin, BootstrapOptions options) {
        LibraryInstaller installer = installer(plugin, options);
        Plugin installed = plugin.getProxy().getPluginManager().getPlugin(BootstrapPlatform.BUNGEECORD.pluginName());
        if (installed != null) return installer.checkInstalled(installed.getDescription().getVersion());
        try {
            LibraryInstaller.Placement placement = installer.download(plugin.getFile().getParentFile().toPath());
            placement.commit();
            installer.installed(placement, true);
            if (installer.restartCountdown() > 0) countdown(plugin, installer, installer.restartCountdown());
        } catch (Throwable error) {
            installer.failed(error);
        }
        return false;
    }

    /**
     * Checks that pnLibrary is loaded and is at least {@code minimumVersion}, without downloading anything.
     *
     * @param plugin plugin that needs pnLibrary
     * @param minimumVersion lowest pnLibrary version the plugin works with
     * @return {@code true} when a suitable pnLibrary is loaded
     */
    public static boolean ensureCompatible(Plugin plugin, String minimumVersion) {
        LibraryInstaller installer = installer(plugin, BootstrapOptions.builder(minimumVersion).build());
        Plugin installed = plugin.getProxy().getPluginManager().getPlugin(BootstrapPlatform.BUNGEECORD.pluginName());
        if (installed == null) {
            installer.failed(new IllegalStateException("pnLibrary не установлена"));
            return false;
        }
        return installer.checkInstalled(installed.getDescription().getVersion());
    }

    /** Announces the seconds left, then stops the proxy; the hosting panel or start script brings it back. */
    private static void countdown(Plugin plugin, LibraryInstaller installer, int secondsLeft) {
        if (secondsLeft <= 0) {
            plugin.getProxy().stop(installer.stopReason());
            return;
        }
        if (LibraryInstaller.announces(secondsLeft)) {
            String line = ChatColor.GOLD + installer.countdownLine(secondsLeft);
            plugin.getProxy().getConsole().sendMessage(TextComponent.fromLegacyText(line));
            for (ProxiedPlayer player : plugin.getProxy().getPlayers()) player.sendMessage(TextComponent.fromLegacyText(line));
        }
        plugin.getProxy().getScheduler().schedule(plugin, () -> countdown(plugin, installer, secondsLeft - 1), 1, TimeUnit.SECONDS);
    }

    private static LibraryInstaller installer(Plugin plugin, BootstrapOptions options) {
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
                plugin.getProxy().getConsole().sendMessage(TextComponent.fromLegacyText(line));
            }
        };
        return new LibraryInstaller(BootstrapPlatform.BUNGEECORD, options, plugin.getDescription().getName(), console);
    }

    private static ConsoleTheme theme(ChatColor border, ChatColor accent) {
        return new ConsoleTheme(border.toString(), accent.toString(), ChatColor.WHITE.toString(),
                ChatColor.GRAY.toString(), ChatColor.RESET.toString());
    }
}
