package ru.privatenull.pnlibrary.remote.bukkit;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.Map;

/** Safe, immutable data exposed to a remote policy class. */
public final class RemoteCheckContext {
    private final JavaPlugin plugin;
    private final String pluginVersion;
    private final String serverVersion;
    private final Map<String, String> values;

    RemoteCheckContext(JavaPlugin plugin, Map<String, String> values) {
        this.plugin = plugin;
        this.pluginVersion = plugin.getDescription().getVersion();
        this.serverVersion = plugin.getServer().getVersion();
        this.values = Collections.unmodifiableMap(values);
    }

    public JavaPlugin plugin() { return plugin; }
    public String pluginVersion() { return pluginVersion; }
    public String serverVersion() { return serverVersion; }
    public Map<String, String> values() { return values; }
}
