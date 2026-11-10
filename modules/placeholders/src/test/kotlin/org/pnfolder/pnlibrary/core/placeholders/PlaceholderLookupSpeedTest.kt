package org.pnfolder.pnlibrary.core.placeholders

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAccess
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderCachePolicy
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderKey
import org.pnfolder.pnlibrary.api.plugin.PluginId
import java.util.concurrent.CompletableFuture

class PlaceholderLookupSpeedTest {
    private val names = listOf("score", "kills", "online", "player.{name}", "player.{name}.stat.{stat}", "top.{n}", "pre-{x}-post")
    private val plugins = listOf("alpha", "beta")
    private val modules = listOf("main", "war", "bank")

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

    private class Old(val known: List<Triple<PlaceholderAddress, PlaceholderEntry<*>, Unit>>) {
        fun search(name: String, inScope: (PlaceholderAddress) -> Boolean): Pair<PlaceholderEntry<*>?, Map<String, String>> {
            val candidates = known.filter { inScope(it.first) }.sortedWith(compareBy({ it.first.module != "main" }, { it.first.module }))
            val lowered = name.lowercase()
            candidates.firstOrNull { it.first.name == lowered }?.let { return it.second to emptyMap() }
            return candidates
                .mapNotNull { (a, e, _) -> PlaceholderPatternMatcher.match(a.name, name)?.let { Triple(a, e, it) } }
                .maxByOrNull { (a, _, _) -> PlaceholderPatternMatcher.specificity(a.name) }
                ?.let { it.second to it.third } ?: (null to emptyMap())
        }
    }

    private fun build(): Pair<PlaceholderAddressIndex, Old> {
        val index = PlaceholderAddressIndex()
        val all = mutableListOf<Triple<PlaceholderAddress, PlaceholderEntry<*>, Unit>>()
        for (plugin in plugins) for (module in modules) for (name in names) {
            val address = PlaceholderAddress(plugin, module, name)
            val entry = entry("$plugin-$module", name)
            assertTrue(index.add(address, entry))
            all += Triple(address, entry, Unit)
        }
        return index to Old(all)
    }

    private val lookups: List<String> = buildList {
        addAll(listOf("score", "SCORE", "Kills", "online", "missing", "player.X", "player.Alex.stat.kills", "top.5",
                "top.5.6", "pre-a-post", "pre--post", "player.", "player.a.b"))
        repeat(40) { add("player.n$it.stat.s$it") }
        repeat(40) { add("top.$it") }
        repeat(40) { add("nothing$it") }
    }

    @Test
    fun `address search gives the same entries and parameters as the old scan`() {
        val (index, old) = build()
        var checked = 0
        for (plugin in plugins) for (module in modules) for (name in lookups) {
            val expected = old.search(name) { it.plugin == plugin && it.module == module }
            val actual = index.find(PlaceholderAddress(plugin, module, name.ifEmpty { "x" }).let { it })
            assertSame(expected.first, actual.entry, "$plugin/$module:$name")
            assertEquals(expected.second, actual.parameters, "$plugin/$module:$name")
            checked++
        }
        for (plugin in plugins + "gamma") for (name in lookups) {
            val expected = old.search(name) { it.plugin == plugin }
            val actual = index.findInPlugin(plugin, name)
            assertSame(expected.first, actual.entry, "$plugin:$name")
            assertEquals(expected.second, actual.parameters, "$plugin:$name")
            checked++
        }
        assertTrue(checked > 600)
    }

    @Test
    fun `an exact address checks no pattern and compiles nothing`() {
        val (index, _) = build()
        val compilations = PlaceholderPatternMatcher.compilations
        val evaluations = PlaceholderPatternMatcher.evaluations

        repeat(200) {
            val address = PlaceholderAddress(plugins[it % 2], modules[it % 3], listOf("score", "kills", "online")[it % 3])
            assertTrue(index.find(address).entry != null)
        }
        repeat(50) { assertTrue(index.findInPlugin("alpha", "SCORE").entry != null) }

        assertEquals(0L, PlaceholderPatternMatcher.evaluations - evaluations)
        assertEquals(0L, PlaceholderPatternMatcher.compilations - compilations)
    }

    @Test
    fun `a pattern lookup tries only the patterns of its module and compiles nothing`() {
        val (index, _) = build()
        val compilations = PlaceholderPatternMatcher.compilations
        val evaluations = PlaceholderPatternMatcher.evaluations

        repeat(100) { index.find(PlaceholderAddress("alpha", "war", "player.n$it.stat.k")) }

        assertEquals(0L, PlaceholderPatternMatcher.compilations - compilations)
        // four parameterized names in one module, against 84 registered entries in all
        assertEquals(100L * 4, PlaceholderPatternMatcher.evaluations - evaluations)
    }

    @Test
    fun `entry lookup equals the old scan and an exact key checks no pattern`() {
        val owner = PluginId.of("alpha")
        val system = PluginId.of("pnlibrary")
        val entries = names.associate { "alpha:${it}" to entry("alpha", it) } + ("beta:score" to entry("beta", "score"))
        val lookup = PlaceholderEntryLookup(entries, system)
        for (name in lookups) {
            val expected = entries.values.filter { it.owner == owner }
                .mapNotNull { e -> PlaceholderPatternMatcher.match(e.key.value, name)?.let { e to it } }
                .maxByOrNull { (e, _) -> PlaceholderPatternMatcher.specificity(e.key.value) }
            val actual = lookup.find(owner, name)
            val exact = entries["alpha:${name.lowercase()}"]
            assertSame(exact ?: expected?.first, actual.entry, name)
            if (exact == null) assertEquals(expected?.second.orEmpty(), actual.parameters, name)
        }
        val evaluations = PlaceholderPatternMatcher.evaluations
        repeat(500) { assertTrue(lookup.find(owner, "score").entry != null) }
        assertEquals(0L, PlaceholderPatternMatcher.evaluations - evaluations)
    }

    @Test
    fun `patterns are compiled once at registration`() {
        val before = PlaceholderPatternMatcher.compilations
        val e = entry("alpha", "pattern.only.{x}")
        assertEquals(1L, PlaceholderPatternMatcher.compilations - before)
        repeat(10_000) { e.pattern!!.match("pattern.only.v$it") }
        assertEquals(1L, PlaceholderPatternMatcher.compilations - before)
    }
}
