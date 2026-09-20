package ru.privatenull.pnlibrary.bukkit.compat

import org.bukkit.Bukkit
import ru.privatenull.pnlibrary.common.minecraft.MinecraftVersion

/**
 * Cached runtime capability and Minecraft-version detection for Bukkit implementations.
 *
 * Class probes do not initialize target classes, avoiding side effects from optional server APIs.
 * Capability flags describe API availability, not whether a particular feature is enabled in the
 * server configuration.
 */
object ServerCapabilities {

    /**
     * Resolved Minecraft version. The value is computed once and cached for the
     * lifetime of the server process.
     */
    val minecraftVersion: MinecraftVersion by lazy { BukkitMinecraftVersion.current() }

    /**
     * Raw Minecraft version reported by the server. Useful when
     * [minecraftVersion] is [MinecraftVersion.UNKNOWN].
     */
    val rawMinecraftVersion: String by lazy { BukkitMinecraftVersion.rawCurrent() }

    /** Whether Paper configuration classes are present. */
    val isPaper: Boolean by lazy {
        hasClass("io.papermc.paper.configuration.Configuration") ||
        hasClass("com.destroystokyo.paper.PaperConfig")
    }

    /** Whether the Paper threaded-regions implementation used by Folia is present. */
    val isFolia: Boolean by lazy {
        hasClass("io.papermc.paper.threadedregions.RegionizedServer")
    }

    /** Whether the Purpur configuration class is present. */
    val isPurpur: Boolean by lazy {
        hasClass("org.purpurmc.purpur.PurpurConfig")
    }

    /** Whether either known Leaf configuration class is present. */
    val isLeaf: Boolean by lazy {
        hasClass("org.leaf.LeafConfig") || hasClass("cn.dreeam.leaf.LeafConfig")
    }

    /** Whether the native server exposes a public zero-argument `getTPS` method. */
    val hasTPS: Boolean by lazy {
        hasMethod(Bukkit.getServer().javaClass, "getTPS")
    }

    /** Returns the native TPS windows, or `null` when unavailable or incompatible. */
    fun getTPS(): DoubleArray? {
        if (!hasTPS) return null
        return try {
            val method = Bukkit.getServer().javaClass.getMethod("getTPS")
            method.invoke(Bukkit.getServer()) as DoubleArray
        } catch (_: ReflectiveOperationException) {
            null
        } catch (_: ClassCastException) {
            null
        }
    }

    /** Returns whether [className] can be linked without initializing the class. */
    fun hasClass(className: String): Boolean = try {
            Class.forName(className, false, javaClass.classLoader)
            true
        } catch (_: ClassNotFoundException) {
            false
        } catch (_: LinkageError) {
            false
        }

    /** Returns whether [clazz] exposes a public method with the exact supplied parameter types. */
    fun hasMethod(clazz: Class<*>, methodName: String, vararg paramTypes: Class<*>): Boolean =
        try {
            clazz.getMethod(methodName, *paramTypes)
            true
        } catch (_: ReflectiveOperationException) {
            false
        } catch (_: SecurityException) {
            false
        }
}

