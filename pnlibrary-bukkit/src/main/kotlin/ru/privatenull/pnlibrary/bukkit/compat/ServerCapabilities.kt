package ru.privatenull.pnlibrary.bukkit.compat

import ru.privatenull.pnlibrary.bukkit.version.MinecraftVersion

import org.bukkit.Bukkit

/**
 * Safe runtime capability and version detection without triggering NoClassDefFoundError.
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

    val isPaper: Boolean by lazy {
        hasClass("io.papermc.paper.configuration.Configuration") ||
        hasClass("com.destroystokyo.paper.PaperConfig")
    }

    val isFolia: Boolean by lazy {
        hasClass("io.papermc.paper.threadedregions.RegionizedServer")
    }

    val isPurpur: Boolean by lazy {
        hasClass("org.purpurmc.purpur.PurpurConfig")
    }

    val isLeaf: Boolean by lazy {
        hasClass("org.leaf.LeafConfig") || hasClass("cn.dreeam.leaf.LeafConfig")
    }

    val hasTPS: Boolean by lazy {
        hasMethod(Bukkit.getServer().javaClass, "getTPS")
    }

    fun getTPS(): DoubleArray? {
        if (!hasTPS) return null
        return try {
            val method = Bukkit.getServer().javaClass.getMethod("getTPS")
            method.invoke(Bukkit.getServer()) as DoubleArray
        } catch (_: Exception) {
            null
        }
    }

    fun hasClass(className: String): Boolean {
        return try {
            Class.forName(className)
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun hasMethod(clazz: Class<*>, methodName: String, vararg paramTypes: Class<*>): Boolean {
        return try {
            clazz.getMethod(methodName, *paramTypes)
            true
        } catch (_: Throwable) {
            false
        }
    }
}

