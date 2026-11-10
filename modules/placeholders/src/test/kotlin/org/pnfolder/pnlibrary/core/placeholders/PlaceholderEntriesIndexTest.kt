package org.pnfolder.pnlibrary.core.placeholders

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAccess
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderCachePolicy
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderKey
import org.pnfolder.pnlibrary.api.plugin.PluginId
import java.util.Locale
import java.util.concurrent.CompletableFuture

class PlaceholderEntriesIndexTest {
    private val system = PluginId.of("pnlibrary")
    private val patternKeys = listOf("player.{name}", "player.{name}.stat.{stat}", "top.{n}", "pre-{x}-post")

    private fun entry(owner: String, key: String) = PlaceholderEntry(
        owner = PluginId.of(owner),
        key = PlaceholderKey.of(key, String::class.java),
        resolver = { CompletableFuture.completedFuture("v") },
        updater = null,
        access = PlaceholderAccess.shared(),
        updateAccess = PlaceholderAccess.ownerOnly(),
        cachePolicy = PlaceholderCachePolicy.none(),
        fallback = null,
        adapterLookup = { null },
    )

    private fun registry(exactPerOwner: Int, owners: List<String>): Map<String, PlaceholderEntry<*>> {
        val all = linkedMapOf<String, PlaceholderEntry<*>>()
        for (owner in owners) {
            for (key in patternKeys) all["$owner:${key.lowercase(Locale.ROOT)}"] = entry(owner, key)
            for (i in 0 until exactPerOwner) all["$owner:key$i"] = entry(owner, "key$i")
        }
        all["pnlibrary:version"] = entry("pnlibrary", "version")
        return all
    }

    private fun oldFind(entries: Map<String, PlaceholderEntry<*>>, consumer: String, reference: String): PlaceholderEntryLookup.Match {
        val separator = reference.indexOf(':')
        val namespace = if (separator > 0) reference.substring(0, separator) else consumer
        val key = if (separator > 0) reference.substring(separator + 1) else reference
        entries["$namespace:${key.lowercase(Locale.ROOT)}"]?.let { return PlaceholderEntryLookup.Match(it, emptyMap()) }
        if (separator < 0) entries["pnlibrary:${key.lowercase(Locale.ROOT)}"]?.let { return PlaceholderEntryLookup.Match(it, emptyMap()) }
        var best: PlaceholderEntry<*>? = null
        var parameters = emptyMap<String, String>()
        for (entry in entries.values) {
            val pattern = entry.pattern ?: continue
            if (entry.owner.value != namespace) continue
            val found = pattern.match(key) ?: continue
            if (best == null || pattern.specificity > best.pattern!!.specificity) {
                best = entry
                parameters = found
            }
        }
        return PlaceholderEntryLookup.Match(best, parameters)
    }

    private val references: List<String> = buildList {
        addAll(listOf("key1", "KEY2", "version", "missing", "player.X", "player.Alex.stat.kills", "top.5", "top.5.6",
            "pre-a-post", "pre--post", "player.", "alpha:player.Bob", "beta:top.7", "gamma:top.7", "alpha:key3", "alpha:nothing"))
        repeat(60) { add("player.n$it.stat.s$it") }
        repeat(60) { add("top.$it") }
        repeat(60) { add("alpha:nothing$it") }
        repeat(30) { add("beta:player.p$it") }
    }

    @Test
    fun `lookup through the owner index equals the old scan over all entries`() {
        val entries = registry(exactPerOwner = 20, owners = listOf("alpha", "beta"))
        val lookup = PlaceholderEntryLookup(entries, system)
        var checked = 0
        for (consumer in listOf("alpha", "beta", "gamma")) for (reference in references) {
            val expected = oldFind(entries, consumer, reference)
            val actual = lookup.find(PluginId.of(consumer), reference)
            assertSame(expected.entry, actual.entry, "$consumer/$reference")
            assertEquals(expected.parameters, actual.parameters, "$consumer/$reference")
            checked++
        }
        assertTrue(checked > 200)
    }

    @Test
    fun `a miss or a pattern looks only at the owner's patterns however many entries exist`() {
        val scanned = listOf(100, 2000).map { total ->
            val entries = registry(exactPerOwner = total / 4, owners = listOf("alpha", "beta", "gamma", "delta"))
            val lookup = PlaceholderEntryLookup(entries, system)
            repeat(50) { lookup.find(PluginId.of("alpha"), "missing$it") }
            repeat(50) { lookup.find(PluginId.of("alpha"), "player.n$it.stat.s$it") }
            lookup.candidatesScanned
        }
        assertEquals(100L * patternKeys.size, scanned[0])
        assertEquals(scanned[0], scanned[1])
    }

    @Test
    fun `removed and replaced entries leave the pattern index`() {
        val entries = PlaceholderEntries()
        val first = entry("alpha", "top.{n}")
        assertTrue(entries.add("alpha:top.{n}", first))
        assertTrue(!entries.add("alpha:top.{n}", entry("alpha", "top.{n}")))
        assertEquals(listOf(first), entries.patternsOf(PluginId.of("alpha")))
        assertTrue(!entries.remove("alpha:top.{n}", entry("alpha", "top.{n}")))
        assertSame(first, entries.remove("alpha:top.{n}"))
        assertTrue(entries.patternsOf(PluginId.of("alpha")).isEmpty())
        val second = entry("alpha", "top.{n}")
        entries["alpha:top.{n}"] = second
        assertEquals(listOf(second), entries.patternsOf(PluginId.of("alpha")))
        assertNull(PlaceholderEntryLookup(entries, system).find(PluginId.of("beta"), "top.1").entry)
    }
}
