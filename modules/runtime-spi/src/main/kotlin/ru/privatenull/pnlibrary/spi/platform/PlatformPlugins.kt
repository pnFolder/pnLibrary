package ru.privatenull.pnlibrary.spi.platform

/**
 * Shared view of the native plugins installed on one platform.
 *
 * An adapter only says how to list its plugins, how to recognize a native owner and how to describe one plugin;
 * owner metadata, installed versions and owner validation then behave the same way on every platform.
 *
 * @param platformName platform name used in validation messages, for example `Bukkit`
 * @param installed lists the currently installed native plugins
 * @param resolve returns the native plugin an owner object stands for, or null when it is not one
 * @param describe converts one native plugin to the shared snapshot contract
 */
class PlatformPlugins<P : Any>(
    private val platformName: String,
    private val installed: () -> Collection<P>,
    private val resolve: (owner: Any) -> P?,
    private val describe: (P) -> PluginSnapshot,
) {
    /** Native plugin that [owner] stands for, or null. */
    fun find(owner: Any): P? = resolve(owner)

    /** Metadata of [owner]; an unsupported owner is reported through [logging] and yields null. */
    fun metadata(owner: Any, logging: PlatformLogging): PluginSnapshot? =
        find(owner)?.let(describe) ?: unsupportedOwner(owner, logging)

    /** Metadata of every installed plugin. */
    fun snapshots(): List<PluginSnapshot> = installed().map(describe)

    /** Installed plugin identifiers mapped to their versions. */
    fun versions(): Map<String, String> = snapshots().associate { it.id to it.version }

    /**
     * Native plugin of an [owner] that must be installed on this platform.
     *
     * @param role what the owner is used for; it starts the failure message, for example `Remote policy owner`
     * @throws IllegalArgumentException when [owner] is not a native plugin or is not installed
     */
    fun requireInstalled(owner: Any, role: String): P {
        val plugin = requireNotNull(find(owner)) {
            "$role must be a $platformName plugin; received ${owner.javaClass.name}"
        }
        require(plugin in installed()) { "$role is not installed on $platformName: ${owner.javaClass.name}" }
        return plugin
    }
}
