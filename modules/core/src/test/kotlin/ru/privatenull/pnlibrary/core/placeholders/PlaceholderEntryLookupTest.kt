package ru.privatenull.pnlibrary.core.placeholders

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderAccess
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderCachePolicy
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderKey
import ru.privatenull.pnlibrary.api.plugin.PluginId
import java.util.concurrent.CompletableFuture

class PlaceholderEntryLookupTest {
    private val consumer = PluginId.of("consumer")
    private val system = PluginId.of("pnlibrary")

    @Test
    fun `prefers exact consumer entry then falls back to system namespace`() {
        val consumerEntry = entry(consumer, "status")
        val systemEntry = entry(system, "version")
        val lookup = PlaceholderEntryLookup(
            mapOf("consumer:status" to consumerEntry, "pnlibrary:version" to systemEntry),
            system,
        )

        assertEquals(consumerEntry, lookup.find(consumer, "status").entry)
        assertEquals(systemEntry, lookup.find(consumer, "version").entry)
        assertNull(lookup.find(consumer, "missing").entry)
    }

    @Test
    fun `selects the most specific matching pattern and returns parameters`() {
        val broad = entry(consumer, "player.{name}")
        val specific = entry(consumer, "player.{name}.stat.{stat}")
        val lookup = PlaceholderEntryLookup(
            mapOf("consumer:broad" to broad, "consumer:specific" to specific),
            system,
        )

        val match = lookup.find(consumer, "player.Alex.stat.kills")

        assertEquals(specific, match.entry)
        assertEquals(mapOf("name" to "Alex", "stat" to "kills"), match.parameters)
    }

    private fun entry(owner: PluginId, key: String) = PlaceholderEntry(
        owner = owner,
        key = PlaceholderKey.of(key, String::class.java),
        resolver = { CompletableFuture.completedFuture("value") },
        updater = null,
        access = PlaceholderAccess.shared(),
        updateAccess = PlaceholderAccess.ownerOnly(),
        cachePolicy = PlaceholderCachePolicy.none(),
        fallback = null,
        adapterLookup = { null },
    )
}
