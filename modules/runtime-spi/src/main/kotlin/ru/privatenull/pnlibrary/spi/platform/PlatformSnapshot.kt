package ru.privatenull.pnlibrary.spi.platform

/** Immutable platform snapshot used by diagnostics and lifecycle messages. */
data class PlatformSnapshot(
    val name: String,
    val version: String,
    val vendor: String? = null,
    val onlinePlayers: Int? = null,
    val registeredServers: List<String> = emptyList(),
    val plugins: List<PluginSnapshot> = emptyList(),
    val java: JavaSnapshot = JavaSnapshot.current(),
) {
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

    companion object {
        fun unknown(platform: String) = PlatformSnapshot(platform, "неизвестна")
    }
}

/** JVM metadata collected from the running process. */
data class JavaSnapshot(val version: String, val vendor: String, val name: String) {
    companion object {
        fun current(): JavaSnapshot = JavaSnapshot(
            System.getProperty("java.version", "unknown"),
            System.getProperty("java.vendor", "unknown"),
            System.getProperty("java.vm.name", "unknown"),
        )
    }
}

/** Immutable metadata for one installed platform plugin. */
data class PluginSnapshot(
    val id: String,
    val name: String,
    val version: String,
    val authors: List<String> = emptyList(),
    val mainClass: String? = null,
) {
    fun asMap(): Map<String, Any?> = linkedMapOf(
        "id" to id,
        "name" to name,
        "version" to version,
        "authors" to authors,
        "mainClass" to mainClass,
    )
}
