package ru.privatenull.pnlibrary.remote.bukkit;

import ru.privatenull.pnlibrary.common.minecraft.MinecraftVersion;
import ru.privatenull.pnlibrary.common.minecraft.MinecraftVersionInfo;

/** Immutable snapshot of the server implementation and Minecraft release. */
public final class BukkitServerInfo {
    private final BukkitPlatform platform;
    private final String platformName;
    private final String version;
    private final MinecraftVersionInfo minecraftVersion;

    BukkitServerInfo(BukkitPlatform platform, String platformName, String version,
                     MinecraftVersionInfo minecraftVersion) {
        this.platform = platform;
        this.platformName = platformName;
        this.version = version;
        this.minecraftVersion = minecraftVersion;
    }

    public BukkitPlatform platform() { return platform; }
    /** Original name returned by Bukkit, useful for an unrecognised fork. */
    public String platformName() { return platformName; }
    /** Stable, lower-case identifier suitable for configuration and comparisons. */
    public String platformKey() {
        return normalize(platformName);
    }
    /** Case-insensitive check that also works for platforms unknown to the library. */
    public boolean isPlatform(String expectedName) {
        return expectedName != null && platformKey().equals(normalize(expectedName));
    }
    /** Whether this is a platform which was not recognised by the current library release. */
    public boolean isUnknownPlatform() { return platform == BukkitPlatform.UNKNOWN; }
    /** Full server version string returned by Bukkit. */
    public String version() { return version; }
    public MinecraftVersionInfo minecraftVersionInfo() { return minecraftVersion; }
    public MinecraftVersion minecraftVersion() { return minecraftVersion.getParsed(); }

    private static String normalize(String value) {
        if (value == null) return "";
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = Character.toLowerCase(value.charAt(i));
            if (Character.isLetterOrDigit(c)) result.append(c);
        }
        return result.toString();
    }
}
