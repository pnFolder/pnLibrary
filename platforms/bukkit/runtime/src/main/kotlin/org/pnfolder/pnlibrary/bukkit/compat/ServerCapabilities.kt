package org.pnfolder.pnlibrary.bukkit.compat

import org.bukkit.Bukkit
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion

/**
 * Cached runtime capability and Minecraft-version detection for Bukkit implementations.
 *
 * Class probes do not initialize target classes, avoiding side effects from optional server APIs.
 * Capability flags describe API availability, not whether a particular feature is enabled in the
 * server configuration.
 */
internal object ServerCapabilities {

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

    val isPaper: Boolean by lazy {
        hasClass("io.papermc.paper.configuration.Configuration") ||
        hasClass("com.destroystokyo.paper.PaperConfig")
    }

    /** Whether the Paper threaded-regions implementation used by Folia is present. */
    val isFolia: Boolean by lazy {
        hasClass("io.papermc.paper.threadedregions.RegionizedServer")
    }

    val isPurpur: Boolean by lazy {
        hasClass("org.purpurmc.purpur.PurpurConfig")
    }

    val isLeaf: Boolean by lazy {
        hasClass("org.leaf.LeafConfig") || hasClass("cn.dreeam.leaf.LeafConfig")
    }

    // Paper and its forks add getTPS; Spigot has no such method.
    private val tpsMethod by lazy {
        runCatching { Bukkit.getServer().javaClass.getMethod("getTPS") }.getOrNull()
    }

    val hasTPS: Boolean get() = tpsMethod != null

    /** The TPS over the last 1, 5 and 15 minutes, or `null` when the server does not report it. */
    fun getTPS(): DoubleArray? = tpsMethod?.let { method ->
        runCatching { method.invoke(Bukkit.getServer()) as? DoubleArray }.getOrNull()
    }

    /** Whether [className] can be loaded on this server. */
    fun hasClass(className: String): Boolean =
        try {
            Class.forName(className, false, javaClass.classLoader)
            true
        } catch (_: ClassNotFoundException) {
            false
        } catch (_: LinkageError) {
            false
        }

    /** Whether [clazz] declares or inherits a public method with that name and parameters. */
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
