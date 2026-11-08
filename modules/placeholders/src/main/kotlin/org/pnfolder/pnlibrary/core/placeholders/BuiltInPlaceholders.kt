package org.pnfolder.pnlibrary.core.placeholders

import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAccess
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAdapter
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderCachePolicy
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderKey
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderPublication
import org.pnfolder.pnlibrary.api.plugin.PluginId
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity
import org.pnfolder.pnlibrary.spi.platform.PlatformIdentity
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/** The placeholders of the library itself, `pnlibrary/main:*`, readable by every plugin. */
class BuiltInPlaceholders(
    private val entries: ConcurrentHashMap<String, PlaceholderEntry<*>>,
    private val addresses: PlaceholderAddressIndex,
    private val adapterLookup: (String) -> PlaceholderAdapter?,
) {
    private val owner = PluginId.of(PnLibraryIdentity.PRODUCT_ID)

    /** Registers the server and runtime placeholders and the `defaultValue` command. */
    fun registerAll(platform: PlatformIdentity, valueStore: GlobalPlaceholderValueStore) {
        add("server.platform", String::class.java) { platform.type.displayName }
        add("server.implementation", String::class.java) { platform.implementationName }
        add("server.proxy", Boolean::class.javaObjectType) { platform.isProxy }
        add("runtime.java", String::class.java) { System.getProperty("java.version", "unknown") }
        registerDefaultValueCommand(valueStore)
    }

    /** Registers one read-only library placeholder, [key], whose value comes from [value] on every read. */
    fun <T : Any> add(key: String, type: Class<T>, value: () -> T) {
        val typedKey = PlaceholderKey.of(key, type)
        val entry = PlaceholderEntry(
            owner = owner,
            key = typedKey,
            resolver = { CompletableFuture.completedFuture(value()) },
            updater = null,
            access = PlaceholderAccess.shared(),
            updateAccess = PlaceholderAccess.ownerOnly(),
            cachePolicy = PlaceholderCachePolicy.none(),
            fallback = null,
            adapterLookup = adapterLookup,
        )
        entries[id(key)] = entry
        PlaceholderAddress.of(owner.value, PlaceholderAddress.DEFAULT_MODULE, key)?.let { addresses.add(it, entry) }
    }

    private fun registerDefaultValueCommand(valueStore: GlobalPlaceholderValueStore) {
        val commands = DefaultValueCommandResolver(valueStore)
        val commandKey = PlaceholderKey.of("defaultvalue", String::class.java)
        val commandEntry = PlaceholderEntry(
            owner,
            commandKey,
            { request ->
                CompletableFuture.completedFuture(commands.resolve(request.parameters["value"].orEmpty(), request.playerId))
            },
            null,
            PlaceholderAccess.shared(),
            PlaceholderAccess.ownerOnly(),
            cachePolicy = PlaceholderCachePolicy.none(),
            fallback = null,
            adapterLookup = adapterLookup,
        )
        entries[id(commandKey.value)] = commandEntry
        commandEntry.publish(PlaceholderPublication("placeholderapi", PnLibraryIdentity.PRODUCT_ID, "defaultvalue"))
    }

    private fun id(key: String) = "${owner.value}:${key.lowercase(Locale.ROOT)}"
}
