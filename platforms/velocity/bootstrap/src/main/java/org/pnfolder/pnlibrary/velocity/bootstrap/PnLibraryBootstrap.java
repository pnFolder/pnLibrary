package org.pnfolder.pnlibrary.velocity.bootstrap;

import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.pnfolder.pnlibrary.bootstrap.BootstrapConsole;
import org.pnfolder.pnlibrary.bootstrap.BootstrapOptions;
import org.pnfolder.pnlibrary.bootstrap.BootstrapPlatform;
import org.pnfolder.pnlibrary.bootstrap.LibraryInstaller;
import org.pnfolder.pnlibrary.console.ConsoleTheme;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Makes sure a suitable pnLibrary runs before the embedding Velocity plugin uses its API. Shade it into the plugin;
 * it has no dependency on pnLibrary itself.
 *
 * <pre>{@code
 * @Subscribe
 * public void onInitialize(ProxyInitializeEvent event) {
 *     if (!PnLibraryBootstrap.ensureInstalled(proxy, this, "1.0.0")) return;   // the console says why
 *     ...
 * }
 * }</pre>
 *
 * Declare pnLibrary as an optional dependency ({@code @Dependency(id = "pnlibrary", optional = true)}) so Velocity
 * loads it first when it is there. Velocity cannot load a plugin while running: a missing pnLibrary is downloaded
 * into {@code plugins}, and the plugin starts after the next proxy restart. With
 * {@link BootstrapOptions.Builder#restartCountdown(int)} the proxy counts down and stops by itself; otherwise the
 * console shows the command to stop it.
 */
public final class PnLibraryBootstrap {
    private PnLibraryBootstrap() {}

    /**
     * Downloads pnLibrary from the default repository when it is missing, or checks the installed version.
     *
     * @param proxy the Velocity server
     * @param plugin instance of the plugin that needs pnLibrary
     * @param minimumVersion lowest pnLibrary version the plugin works with
     * @return {@code true} when a suitable pnLibrary is loaded; {@code false} after a download that needs a restart
     */
    public static boolean ensureInstalled(ProxyServer proxy, Object plugin, String minimumVersion) {
        return ensureInstalled(proxy, plugin, BootstrapOptions.builder(minimumVersion).build());
    }

    /**
     * Downloads pnLibrary according to {@code options} when it is missing, or checks the installed version.
     *
     * @param proxy the Velocity server
     * @param plugin instance of the plugin that needs pnLibrary
     * @param options minimum version, repository and size limit
     * @return {@code true} when a suitable pnLibrary is loaded; {@code false} after a download that needs a restart
     */
    public static boolean ensureInstalled(ProxyServer proxy, Object plugin, BootstrapOptions options) {
        LibraryInstaller installer = installer(proxy, plugin, options);
        Optional<PluginContainer> installed = proxy.getPluginManager().getPlugin(BootstrapPlatform.VELOCITY.pluginName());
        if (installed.isPresent()) return installer.checkInstalled(versionOf(installed.get()));
        try {
            LibraryInstaller.Placement placement = installer.download(pluginsDirectory(proxy, plugin));
            placement.commit();
            installer.installed(placement, true);
            if (installer.restartCountdown() > 0) countdown(proxy, plugin, installer, installer.restartCountdown());
        } catch (Throwable error) {
            installer.failed(error);
        }
        return false;
    }

    /**
     * Checks that pnLibrary is loaded and is at least {@code minimumVersion}, without downloading anything.
     *
     * @param proxy the Velocity server
     * @param plugin instance of the plugin that needs pnLibrary
     * @param minimumVersion lowest pnLibrary version the plugin works with
     * @return {@code true} when a suitable pnLibrary is loaded
     */
    public static boolean ensureCompatible(ProxyServer proxy, Object plugin, String minimumVersion) {
        LibraryInstaller installer = installer(proxy, plugin, BootstrapOptions.builder(minimumVersion).build());
        Optional<PluginContainer> installed = proxy.getPluginManager().getPlugin(BootstrapPlatform.VELOCITY.pluginName());
        if (!installed.isPresent()) {
            installer.failed(new IllegalStateException("pnLibrary не установлена"));
            return false;
        }
        return installer.checkInstalled(versionOf(installed.get()));
    }

    /** Announces the seconds left, then stops the proxy; the hosting panel or start script brings it back. */
    private static void countdown(ProxyServer proxy, Object plugin, LibraryInstaller installer, int secondsLeft) {
        if (secondsLeft <= 0) {
            proxy.shutdown(Component.text(installer.stopReason()));
            return;
        }
        if (LibraryInstaller.announces(secondsLeft)) {
            Component line = Component.text(installer.countdownLine(secondsLeft), NamedTextColor.GOLD);
            proxy.getConsoleCommandSource().sendMessage(line);
            proxy.getAllPlayers().forEach(player -> player.sendMessage(line));
        }
        proxy.getScheduler().buildTask(plugin, () -> countdown(proxy, plugin, installer, secondsLeft - 1))
                .delay(1, TimeUnit.SECONDS).schedule();
    }

    private static String versionOf(PluginContainer library) {
        return library.getDescription().getVersion().orElse("0.0.0");
    }

    /** The folder the plugin's own jar was loaded from, or {@code plugins} next to the working directory. */
    private static Path pluginsDirectory(ProxyServer proxy, Object plugin) {
        return proxy.getPluginManager().fromInstance(plugin)
                .flatMap(container -> container.getDescription().getSource())
                .map(Path::getParent)
                .orElse(Paths.get("plugins"));
    }

    private static LibraryInstaller installer(ProxyServer proxy, Object plugin, BootstrapOptions options) {
        String name = proxy.getPluginManager().fromInstance(plugin)
                .map(container -> container.getDescription().getName().orElse(container.getDescription().getId()))
                .orElse(plugin.getClass().getSimpleName());
        BootstrapConsole console = new BootstrapConsole() {
            @Override
            public ConsoleTheme theme(Tone tone) {
                return ConsoleTheme.plain();
            }

            @Override
            public void send(String line) {
                proxy.getConsoleCommandSource().sendMessage(Component.text(line));
            }
        };
        return new LibraryInstaller(BootstrapPlatform.VELOCITY, options, name, console);
    }
}
