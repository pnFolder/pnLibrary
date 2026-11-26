package org.pnfolder.pnlibrary.bukkit.compat;

import java.lang.reflect.Method;

import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion;

/**
 * Cached runtime capability and Minecraft-version detection for Bukkit implementations.
 *
 * <p>Class probes do not initialize target classes, avoiding side effects from optional server APIs.
 * Capability flags describe API availability, not whether a particular feature is enabled in the server
 * configuration.
 */
public final class ServerCapabilities {
    private static volatile MinecraftVersion minecraftVersion;
    private static volatile String rawMinecraftVersion;
    private static volatile Boolean folia;
    private static volatile Boolean paper;
    private static volatile Method tpsMethod;
    private static volatile boolean tpsResolved;

    private ServerCapabilities() {
    }

    /** Resolved Minecraft version, computed once and cached for the lifetime of the server process. */
    @NotNull
    public static MinecraftVersion getMinecraftVersion() {
        MinecraftVersion value = minecraftVersion;
        if (value == null) {
            value = BukkitMinecraftVersion.current();
            minecraftVersion = value;
        }
        return value;
    }

    /** Raw Minecraft version reported by the server; useful when the parsed one is unknown. */
    @NotNull
    public static String getRawMinecraftVersion() {
        String value = rawMinecraftVersion;
        if (value == null) {
            value = BukkitMinecraftVersion.rawCurrent();
            rawMinecraftVersion = value;
        }
        return value;
    }

    public static boolean isPaper() {
        Boolean value = paper;
        if (value == null) {
            value = hasClass("io.papermc.paper.configuration.Configuration")
                || hasClass("com.destroystokyo.paper.PaperConfig");
            paper = value;
        }
        return value;
    }

    /** Whether the Paper threaded-regions implementation used by Folia is present. */
    public static boolean isFolia() {
        Boolean value = folia;
        if (value == null) {
            value = hasClass("io.papermc.paper.threadedregions.RegionizedServer");
            folia = value;
        }
        return value;
    }

    public static boolean isPurpur() {
        return hasClass("org.purpurmc.purpur.PurpurConfig");
    }

    public static boolean isLeaf() {
        return hasClass("org.leaf.LeafConfig") || hasClass("cn.dreeam.leaf.LeafConfig");
    }

    public static boolean hasTPS() {
        return tpsMethod() != null;
    }

    /** The TPS over the last 1, 5 and 15 minutes, or {@code null} when the server does not report it. */
    @Nullable
    public static double[] getTPS() {
        Method method = tpsMethod();
        if (method == null) {
            return null;
        }
        try {
            Object value = method.invoke(Bukkit.getServer());
            return value instanceof double[] ? (double[]) value : null;
        } catch (ReflectiveOperationException | RuntimeException error) {
            return null;
        }
    }

    /** Whether {@code className} can be loaded on this server. */
    public static boolean hasClass(@NotNull String className) {
        try {
            Class.forName(className, false, ServerCapabilities.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError error) {
            return false;
        }
    }

    /** Whether {@code type} declares or inherits a public method with that name and parameters. */
    public static boolean hasMethod(@NotNull Class<?> type, @NotNull String methodName, @NotNull Class<?>... paramTypes) {
        try {
            type.getMethod(methodName, paramTypes);
            return true;
        } catch (ReflectiveOperationException | SecurityException error) {
            return false;
        }
    }

    // Paper and its forks add getTPS; Spigot has no such method.
    private static Method tpsMethod() {
        if (!tpsResolved) {
            try {
                tpsMethod = Bukkit.getServer().getClass().getMethod("getTPS");
            } catch (ReflectiveOperationException | RuntimeException error) {
                tpsMethod = null;
            }
            tpsResolved = true;
        }
        return tpsMethod;
    }
}
