package org.pnfolder.pnlibrary.bungee;

import net.byteflux.libby.BungeeLibraryManager;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.plugin.Plugin;
import org.pnfolder.pnlibrary.console.ConsoleTheme;
import org.pnfolder.pnlibrary.loader.LibraryLoader;
import org.pnfolder.pnlibrary.loader.LibrarySettings;
import org.pnfolder.pnlibrary.loader.RuntimeLibraries;
import org.pnfolder.pnlibrary.loader.StartupFailure;

import java.lang.reflect.Constructor;

/**
 * BungeeCord entry point for pnLibrary. All startup and shutdown work lives in {@code BungeeRuntime}.
 *
 * <p>Plain Java on purpose: the light JAR has no Kotlin until {@link LibraryLoader} has added it, so the Kotlin
 * runtime is created by reflection after that.
 */
public final class PnLibraryBungeePlugin extends Plugin {
    private Object runtime;

    /** Starts the runtime; a refused remote policy or failed startup stops it. */
    @Override
    public void onEnable() {
        LibrarySettings settings = LibrarySettings.DEFAULT;
        try {
            LibraryLoader.load(new BungeeLibraryManager(this, settings.directoryFor(getDataFolder().toPath())), getClass().getClassLoader(), settings, RuntimeLibraries.BUNGEE);
            Class<?> type = Class.forName("org.pnfolder.pnlibrary.bungee.BungeeRuntime", true, getClass().getClassLoader());
            Constructor<?> constructor = type.getDeclaredConstructors()[0];
            runtime = constructor.newInstance(this);
            type.getMethod("start").invoke(runtime);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            report(settings, failure);
        }
    }

    private void report(LibrarySettings settings, Throwable failure) {
        try {
            ConsoleTheme theme = new ConsoleTheme(ChatColor.DARK_RED.toString(), ChatColor.RED.toString(),
                    ChatColor.WHITE.toString(), ChatColor.GRAY.toString(), ChatColor.RESET.toString());
            StartupFailure.report(theme,
                    line -> getProxy().getConsole().sendMessage(TextComponent.fromLegacyText(line)), settings,
                    getDataFolder().toPath(), failure);
        } catch (RuntimeException | LinkageError cardFailed) {
            getLogger().severe("pnLibrary could not start: " + failure);
        }
    }

    /** Stops the runtime and releases everything it registered. */
    @Override
    public void onDisable() {
        if (runtime == null) {
            return;
        }
        try {
            runtime.getClass().getMethod("stop").invoke(runtime);
        } catch (ReflectiveOperationException failure) {
            getLogger().severe("pnLibrary could not stop cleanly: " + failure);
        }
        runtime = null;
    }
}
