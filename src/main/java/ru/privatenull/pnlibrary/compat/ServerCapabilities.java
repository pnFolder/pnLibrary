package ru.privatenull.pnlibrary.compat;

import org.bukkit.entity.EntityType;
import org.bukkit.plugin.Plugin;

/** Runtime feature detection shared by every plugin that depends on pnLibrary. */
public final class ServerCapabilities {

    private static final boolean DISPLAY_ENTITIES = detectDisplayEntities();

    private ServerCapabilities() {
    }

    public static boolean hasDisplayEntities() {
        return DISPLAY_ENTITIES;
    }

    public static boolean hasClass(String className) {
        if (className == null || className.isBlank()) return false;
        try {
            Class.forName(className, false, ServerCapabilities.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void logEnvironment(Plugin plugin) {
        plugin.getLogger().info("Minecraft server: " + ServerVersion.current());
        plugin.getLogger().info("Display Entity API: " + (DISPLAY_ENTITIES ? "available" : "unavailable"));
    }

    private static boolean detectDisplayEntities() {
        try {
            EntityType.valueOf("ITEM_DISPLAY");
            EntityType.valueOf("TEXT_DISPLAY");
            EntityType.valueOf("BLOCK_DISPLAY");
            return hasClass("org.bukkit.entity.ItemDisplay")
                    && hasClass("org.bukkit.entity.TextDisplay")
                    && hasClass("org.bukkit.entity.BlockDisplay");
        } catch (Throwable ignored) {
            return false;
        }
    }
}
