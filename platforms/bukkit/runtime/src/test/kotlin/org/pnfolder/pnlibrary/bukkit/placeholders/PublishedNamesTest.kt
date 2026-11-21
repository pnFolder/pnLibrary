package org.pnfolder.pnlibrary.bukkit.placeholders

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PublishedNamesTest {
    private val handlers = mapOf("war~score" to "plain", "war~score.{who}" to "player", "war~score.top" to "top", "tag" to "tag")

    private fun index(names: Map<String, String>) = PublishedNames<String>().also { index ->
        names.forEach { (name, handler) -> assertTrue(index.add(name, handler)) }
    }

    /** The lookup as it was before the index: a walk over every published name. */
    private fun scan(names: Map<String, String>, params: String): PublishedNames.Found<String>? {
        names.entries.firstOrNull { '{' !in it.key && params.equals(it.key, true) }?.let { return PublishedNames.Found(it.value, emptyMap()) }
        val byPattern = names.entries
            .mapNotNull { (name, handler) -> ExternalNames.match(name, params)?.let { Triple(name, handler, it) } }
            .maxByOrNull { (name, _, _) -> ExternalNames.specificity(name) }
        if (byPattern != null) return PublishedNames.Found(byPattern.second, byPattern.third)
        val match = names.entries.sortedByDescending { it.key.length }.firstOrNull {
            '{' !in it.key && (params.equals(it.key, true) || params.startsWith(it.key + "_", true))
        } ?: return null
        val suffix = params.removePrefix(match.key).removePrefix("_")
        return PublishedNames.Found(match.value, if (suffix.isEmpty()) emptyMap() else mapOf("value" to suffix))
    }

    @Test
    fun `a plain name matches exactly`() {
        assertEquals(PublishedNames.Found("plain", emptyMap()), index(handlers).find("war~score"))
    }

    @Test
    fun `a brace part reads the value from the text`() {
        assertEquals(PublishedNames.Found("player", mapOf("who" to "Alex")), index(handlers).find("war~score.Alex"))
    }

    @Test
    fun `fixed text beats a brace part`() {
        assertEquals("top", index(handlers).find("war~score.top")?.handler)
    }

    @Test
    fun `the rest after an underscore goes to the value`() {
        assertEquals(PublishedNames.Found("tag", mapOf("value" to "x")), index(handlers).find("tag_x"))
    }

    @Test
    fun `an unknown name gives nothing`() {
        assertNull(index(handlers).find("other"))
    }

    @Test
    fun `a taken name is refused and a removed one is gone`() {
        val names = index(handlers)
        assertFalse(names.add("tag", "again"))
        names.remove("tag")
        assertNull(names.find("tag"))
        assertFalse(names.isEmpty)
    }

    @Test
    fun `the index answers like the walk over all names`() {
        val names = buildMap {
            repeat(300) { put("m$it~score", "plain$it"); put("m$it~top.{n}", "top$it"); put("m$it~p.{a}.{b}", "pair$it") }
            put("tag", "tag"); put("tag_long", "long")
        }
        val index = index(names)
        val asked = buildList {
            repeat(300) { add("m$it~score"); add("M$it~SCORE"); add("m$it~top.$it"); add("m$it~p.x.y"); add("m$it~p.x") }
            addAll(listOf("tag", "tag_x", "TAG_x", "tag_long", "tag_long_more", "nothing", "", "m5~score_extra", "m5~top.1.2"))
        }
        for (params in asked) assertEquals(scan(names, params), index.find(params), params)
    }

    @Test
    fun `a direct hit tries no name one by one`() {
        val names = buildMap { repeat(500) { put("m$it~score", "plain$it"); put("m$it~top.{n}", "top$it") } }
        val index = index(names)

        repeat(2_000) { assertEquals("plain${it % 500}", index.find("m${it % 500}~score")?.handler) }

        assertEquals(0L, index.scanned)
        index.find("m1~top.7")
        assertEquals(500L, index.scanned) // only the parameterized names, never the 500 plain ones
    }

    @Test
    fun `the server thread does not wait for placeholder values`() {
        assertEquals(0L, MAIN_THREAD_WAIT_MILLIS)
        assertTrue(OTHER_THREAD_WAIT_MILLIS > 0)
    }
}
