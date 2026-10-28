package org.pnfolder.pnlibrary.bukkit;

import net.byteflux.libby.BukkitLibraryManager;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;
import org.pnfolder.pnlibrary.console.ConsoleTheme;
import org.pnfolder.pnlibrary.loader.LibraryLoader;
import org.pnfolder.pnlibrary.loader.LibrarySettings;
import org.pnfolder.pnlibrary.loader.RuntimeLibraries;
import org.pnfolder.pnlibrary.loader.StartupFailure;

import java.lang.reflect.Constructor;

/**
 * Bukkit entry point for pnLibrary. All startup and shutdown work lives in {@code BukkitRuntime}.
 *
 * <p>Plain Java on purpose: the light JAR has no Kotlin until {@link LibraryLoader} has added it, so the Kotlin
 * runtime is created by reflection after that.
 */
public final class PnLibraryBukkitPlugin extends JavaPlugin {
    private Object runtime;

    @Override
    public void onEnable() {
        LibrarySettings settings = LibrarySettings.DEFAULT;
        try {
            LibraryLoader.load(new BukkitLibraryManager(this, settings.directoryFor(getDataFolder().toPath())), getClassLoader(), settings, RuntimeLibraries.BUKKIT);
            Class<?> type = Class.forName("org.pnfolder.pnlibrary.bukkit.BukkitRuntime", true, getClassLoader());
            Constructor<?> constructor = type.getDeclaredConstructors()[0];
            runtime = constructor.newInstance(this);
            type.getMethod("start").invoke(runtime);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            report(settings, failure);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void report(LibrarySettings settings, Throwable failure) {
        try {
            ConsoleTheme theme = new ConsoleTheme(ChatColor.DARK_RED.toString(), ChatColor.RED.toString(),
                    ChatColor.WHITE.toString(), ChatColor.GRAY.toString(), ChatColor.RESET.toString());
            StartupFailure.report(theme, line -> getServer().getConsoleSender().sendMessage(line), settings,
                    getDataFolder().toPath(), failure);
        } catch (RuntimeException | LinkageError cardFailed) {
            getLogger().severe("pnLibrary could not start: " + failure);
        }
    }

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
