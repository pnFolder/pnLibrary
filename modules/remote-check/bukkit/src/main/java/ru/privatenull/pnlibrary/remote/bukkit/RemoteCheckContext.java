package ru.privatenull.pnlibrary.remote.bukkit;

import org.bukkit.plugin.java.JavaPlugin;
import ru.privatenull.pnlibrary.common.minecraft.MinecraftVersion;
import ru.privatenull.pnlibrary.common.minecraft.MinecraftVersionInfo;

import java.util.Collections;
import java.util.Map;

/** Safe, immutable data exposed to a remote policy class. */
public final class RemoteCheckContext {
    private final JavaPlugin plugin;
    private final String pluginVersion;
    private final String serverVersion;
    private final MinecraftVersion minecraftVersion;
    private final MinecraftVersionInfo minecraftVersionInfo;
    private final Map<String, String> values;

    RemoteCheckContext(JavaPlugin plugin, Map<String, String> values) {
        this.plugin = plugin;
        this.pluginVersion = plugin.getDescription().getVersion();
        this.serverVersion = plugin.getServer().getVersion();
        this.minecraftVersionInfo = MinecraftVersion.parseInfo(serverVersion);
        this.minecraftVersion = minecraftVersionInfo.getParsed();
        this.values = Collections.unmodifiableMap(values);
    }

    public JavaPlugin plugin() { return plugin; }
    public String pluginVersion() { return pluginVersion; }
    public String serverVersion() { return serverVersion; }
    /** Parsed Minecraft version; UNKNOWN is returned when the server exposes an unknown release. */
    public MinecraftVersion minecraftVersion() { return minecraftVersion; }
    /** Full parse result; retains the raw server version when the enum is UNKNOWN. */
    public MinecraftVersionInfo minecraftVersionInfo() { return minecraftVersionInfo; }
    public Map<String, String> values() { return values; }
}
