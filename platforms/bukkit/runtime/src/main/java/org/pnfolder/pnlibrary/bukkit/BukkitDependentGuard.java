package org.pnfolder.pnlibrary.bukkit;

import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;

/**
 * Keeps plugins that depend on a switched-off pnLibrary from running.
 *
 * <p>Bukkit enables them whether or not their dependency started, so each is disabled as it enables. pnLibrary stays
 * enabled, inert, until {@code later} runs after the server has started, then switches itself off.
 */
final class BukkitDependentGuard implements Listener {
    private final Plugin plugin;
    private final Consumer<Runnable> later;
    private final Consumer<String> log;

    BukkitDependentGuard(Plugin plugin, Consumer<Runnable> later, Consumer<String> log) {
        this.plugin = plugin;
        this.later = later;
        this.log = log;
    }

    /** Disables the dependents already running, then watches for those that enable later. */
    void arm() {
        disableDependentsOf(plugin, log);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        later.accept(() -> Bukkit.getPluginManager().disablePlugin(plugin));
    }

    @EventHandler
    public void onEnable(PluginEnableEvent event) {
        if (dependsOn(event.getPlugin(), plugin)) {
            disable(event.getPlugin(), plugin, log);
        }
    }

    /** Disables every enabled plugin that depends on {@code owner}; used when {@code owner} fails to start. */
    static void disableDependentsOf(Plugin owner, Consumer<String> log) {
        for (Plugin candidate : Bukkit.getPluginManager().getPlugins()) {
            if (candidate != owner && candidate.isEnabled() && dependsOn(candidate, owner)) {
                disable(candidate, owner, log);
            }
        }
    }

    private static boolean dependsOn(Plugin candidate, Plugin owner) {
        PluginDescriptionFile description = candidate.getDescription();
        return candidate != owner && (description.getDepend().contains(owner.getName())
            || description.getSoftDepend().contains(owner.getName()));
    }

    private static void disable(Plugin candidate, Plugin owner, Consumer<String> log) {
        log.accept(candidate.getName() + " отключён: " + owner.getName() + " не запущен");
        Bukkit.getPluginManager().disablePlugin(candidate);
    }
}
