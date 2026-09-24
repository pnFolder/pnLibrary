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
    private final BukkitServerInfo serverInfo;
    private final Map<String, String> values;

    RemoteCheckContext(JavaPlugin plugin, Map<String, String> values) {
        this.plugin = plugin;
        this.pluginVersion = plugin.getDescription().getVersion();
        this.serverVersion = plugin.getServer().getVersion();
        this.minecraftVersionInfo = MinecraftVersion.parseInfo(serverVersion);
        this.minecraftVersion = minecraftVersionInfo.getParsed();
        String platformName = plugin.getServer().getName();
        this.serverInfo = new BukkitServerInfo(detectPlatform(platformName), platformName, serverVersion,
            minecraftVersionInfo);
        this.values = Collections.unmodifiableMap(values);
    }

    public JavaPlugin plugin() { return plugin; }
    public String pluginVersion() { return pluginVersion; }
    public String serverVersion() { return serverVersion; }
    /** Parsed Minecraft version; UNKNOWN is returned when the server exposes an unknown release. */
    public MinecraftVersion minecraftVersion() { return minecraftVersion; }
    /** Full parse result; retains the raw server version when the enum is UNKNOWN. */
    public MinecraftVersionInfo minecraftVersionInfo() { return minecraftVersionInfo; }
    /** Combined server core and Minecraft version snapshot. */
    public BukkitServerInfo serverInfo() { return serverInfo; }
    public Map<String, String> values() { return values; }

    private static BukkitPlatform detectPlatform(String name) {
        String normalized = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("purpur")) return BukkitPlatform.PURPUR;
        if (normalized.contains("folia")) return BukkitPlatform.FOLIA;
        if (normalized.contains("paper")) return BukkitPlatform.PAPER;
        if (normalized.contains("spigot")) return BukkitPlatform.SPIGOT;
        if (normalized.contains("craftbukkit")) return BukkitPlatform.CRAFTBUKKIT;
        if (normalized.contains("bukkit")) return BukkitPlatform.BUKKIT;
        return BukkitPlatform.UNKNOWN;
    }
}
