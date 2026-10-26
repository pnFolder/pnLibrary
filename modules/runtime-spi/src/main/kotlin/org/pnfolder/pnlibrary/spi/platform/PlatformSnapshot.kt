package org.pnfolder.pnlibrary.spi.platform

/** Immutable platform snapshot used by diagnostics and lifecycle messages. */
data class PlatformSnapshot(
    /** Platform product name. */
    val name: String,
    /** Platform version string. */
    val version: String,
    /** Platform vendor, when the platform reports one. */
    val vendor: String? = null,
    /** Number of online players, or `null` when unknown. */
    val onlinePlayers: Int? = null,
    /** Names of backend servers registered with a proxy. */
    val registeredServers: List<String> = emptyList(),
    /** Installed plugins reported by the platform. */
    val plugins: List<PluginSnapshot> = emptyList(),
    /** JVM the platform runs on. */
    val java: JavaSnapshot = JavaSnapshot.current(),
) {
    /** Flat map form of this snapshot, used when rendering diagnostics. */
    fun asMap(): Map<String, Any?> = linkedMapOf(
        "platformName" to name,
        "platformVersion" to version,
        "platformVendor" to vendor,
        "onlinePlayersCount" to onlinePlayers,
        "registeredServersCount" to registeredServers.size,
        "registeredServerNames" to registeredServers,
        "plugins" to plugins.map(PluginSnapshot::asMap),
        "java" to java,
    )

    /** Factories for [PlatformSnapshot]. */
    companion object {
        /** Snapshot for a platform whose details cannot be read. */
        fun unknown(platform: String) = PlatformSnapshot(platform, "неизвестна")
    }
}

/**
 * JVM metadata collected from the running process.
 *
 * @property version Java runtime version
 * @property vendor Java runtime vendor
 * @property name JVM implementation name
 */
data class JavaSnapshot(val version: String, val vendor: String, val name: String) {
    /** Factories for [JavaSnapshot]. */
    companion object {
        /** Reads the metadata of the running JVM; unavailable values are `unknown`. */
        fun current(): JavaSnapshot = JavaSnapshot(
            System.getProperty("java.version", "unknown"),
            System.getProperty("java.vendor", "unknown"),
            System.getProperty("java.vm.name", "unknown"),
        )
    }
}

/**
 * Immutable metadata for one installed platform plugin.
 *
 * @property id plugin identifier
 * @property name display name
 * @property version plugin version
 * @property authors declared authors
 * @property mainClass main class name, when the platform reports one
 */
data class PluginSnapshot(
    val id: String,
    val name: String,
    val version: String,
    val authors: List<String> = emptyList(),
    val mainClass: String? = null,
) {
    /** Flat map form of this plugin entry, used when rendering diagnostics. */
    fun asMap(): Map<String, Any?> = linkedMapOf(
        "id" to id,
        "name" to name,
        "version" to version,
        "authors" to authors,
        "mainClass" to mainClass,
    )
}
