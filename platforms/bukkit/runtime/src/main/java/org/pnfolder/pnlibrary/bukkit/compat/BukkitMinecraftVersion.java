package org.pnfolder.pnlibrary.bukkit.compat;

import java.lang.reflect.Method;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion;

/** Resolves Minecraft versions through Bukkit without leaking Bukkit into the shared version API. */
public final class BukkitMinecraftVersion {
    private BukkitMinecraftVersion() {
    }

    @NotNull
    public static MinecraftVersion current() {
        return MinecraftVersion.parse(rawCurrent());
    }

    @NotNull
    public static String rawCurrent() {
        try {
            Server server = Bukkit.getServer();
            Method method = null;
            for (Method candidate : server.getClass().getMethods()) {
                if (candidate.getName().equals("getMinecraftVersion") && candidate.getParameterTypes().length == 0) {
                    method = candidate;
                    break;
                }
            }
            Object value = method == null ? null : method.invoke(server);
            if (value instanceof String && !((String) value).trim().isEmpty()) {
                return (String) value;
            }
            return bukkitVersion();
        } catch (ReflectiveOperationException | SecurityException error) {
            return bukkitVersion();
        }
    }

    private static String bukkitVersion() {
        String version = Bukkit.getBukkitVersion();
        int dash = version.indexOf('-');
        return dash < 0 ? version : version.substring(0, dash);
    }
}
