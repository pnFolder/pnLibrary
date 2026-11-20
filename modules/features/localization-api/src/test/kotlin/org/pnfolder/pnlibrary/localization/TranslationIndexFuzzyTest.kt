package org.pnfolder.pnlibrary.localization

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayout

class TranslationIndexFuzzyTest {
    private fun index(vararg entries: Pair<String, String>) =
        TranslationIndex.of(entries.map { (key, text) -> TranslationMatch(key, text, key) })

    private fun TranslationIndex<String>.keysFor(text: String, minSimilarity: Double = 0.7, limit: Int = 5) =
        fuzzy(text, minSimilarity, limit).map { it.key }

    @Test
    fun `a wrong letter is tolerated`() {
        assertEquals(listOf("item.apple"), index("item.apple" to "Яблоко").fuzzy("яблако").map { it.key })
    }

    @Test
    fun `swapped neighbouring letters count as one error`() {
        assertEquals(listOf("item.apple"), index("item.apple" to "Яблоко").keysFor("яблкоо"))
    }

    @Test
    fun `two errors in a six letter word need a lower threshold`() {
        val apples = index("item.apple" to "Яблоко")
        assertTrue(apples.keysFor("ябока", minSimilarity = 0.7).isEmpty())
        assertEquals(listOf("item.apple"), apples.keysFor("ябока", minSimilarity = 0.6))
    }

    @Test
    fun `a single word inside a longer name is matched`() {
        assertEquals(listOf("item.golden_apple"), index("item.golden_apple" to "Золотое яблоко").keysFor("яблако"))
    }

    @Test
    fun `closer matches come first even when their key sorts later`() {
        val found = index("a.tree" to "Яблоня", "z.apple" to "Яблоко").keysFor("яблоко", minSimilarity = 0.6)
        assertEquals(listOf("z.apple", "a.tree"), found)
    }

    @Test
    fun `equally similar entries are ordered by key`() {
        assertEquals(listOf("a.x", "b.x"), index("b.x" to "Яблоко", "a.x" to "Яблоко").keysFor("яблоко"))
    }

    @Test
    fun `the limit caps the result`() {
        val apples = index("a" to "Яблоко", "b" to "Яблоко", "c" to "Яблоко")
        assertEquals(listOf("a", "b"), apples.keysFor("яблоко", limit = 2))
    }

    @Test
    fun `blank queries find nothing`() {
        assertTrue(index("item.apple" to "Яблоко").fuzzy("   ").isEmpty())
    }

    @Test
    fun `case and yo are normalized`() {
        assertEquals(listOf("block.spruce"), index("block.spruce" to "Ёлка").keysFor("ЕЛКА"))
        assertEquals(listOf("item.apple"), index("item.apple" to "Яблоко").keysFor("ЯБЛАКО"))
    }

    @Test
    fun `one error in a three letter word is too many at the default threshold`() {
        val bows = index("item.bow" to "Лук")
        assertEquals(listOf("item.bow"), bows.keysFor("лук"))
        assertTrue(bows.fuzzy("люк").isEmpty())
    }

    @Test
    fun `scrambled long words are caught by letter triples`() {
        assertEquals(listOf("block.sunflower"), index("block.sunflower" to "Подсолнух").keysFor("солнухпод"))
    }

    @Test
    fun `out of range arguments are rejected`() {
        val apples = index("item.apple" to "Яблоко")
        assertThrows(IllegalArgumentException::class.java) { apples.fuzzy("яблоко", -0.1, 5) }
        assertThrows(IllegalArgumentException::class.java) { apples.fuzzy("яблоко", 1.1, 5) }
        assertThrows(IllegalArgumentException::class.java) { apples.fuzzy("яблоко", 0.7, 0) }
        assertThrows(IllegalArgumentException::class.java) { apples.fuzzy("яблоко", 0.7, -1) }
        assertThrows(IllegalArgumentException::class.java) { apples.suggest("яблоко", 0) }
        assertThrows(IllegalArgumentException::class.java) { apples.best("яблоко", 1.5) }
    }

    private val food = index("item.apple" to "Яблоко", "item.melon" to "Арбуз", "item.bow" to "Лук")

    @Test
    fun `suggest puts the intended entry on top for damaged words`() {
        for (typed in listOf("яблако", "ябока", "ябло", "яблоко")) {
            assertEquals("item.apple", food.suggest(typed).firstOrNull()?.key, typed)
        }
    }

    @Test
    fun `suggest never throws on short input and finds containing entries`() {
        for (typed in listOf("я", "к", "лю", "ук", "zz", "1")) food.suggest(typed)
        assertEquals(listOf("item.bow"), food.suggest("ук").map { it.key })
        assertTrue(food.suggest("к").isNotEmpty())
    }

    @Test
    fun `suggest is deterministic, deduplicated and capped`() {
        val apples = index("b.k" to "Яблоко", "a.k" to "Яблоко", "c.k" to "Золотое яблоко")
        assertEquals(listOf("a.k", "b.k", "c.k"), apples.suggest("яблоко").map { it.key })
        assertEquals(apples.suggest("яблако"), apples.suggest("яблако"))
        assertEquals(1, apples.suggest("яблоко", 1).size)
    }

    @Test
    fun `best returns the intended entry or null`() {
        assertEquals("item.apple", food.best("яблако")?.key)
        assertNull(food.best("zzzzzz"))
        assertNull(food.best("ябока"))
    }

    @Test
    fun `best breaks a tie by the lowest key`() {
        assertEquals("a.x", index("b.x" to "Яблоко", "a.x" to "Яблоко").best("яблоко")?.key)
    }

    @Test
    fun `empty input suggests and matches nothing`() {
        assertTrue(food.suggest("").isEmpty())
        assertTrue(food.suggest("  ").isEmpty())
        assertNull(food.best(""))
    }

    @Test
    fun `a query typed on the wrong keyboard layout is understood`() {
        assertEquals("item.apple", food.best("z,kjrj")?.key)
        assertEquals("item.apple", food.suggest("z,kjrj").firstOrNull()?.key)
        assertEquals("item.apple", index("item.apple" to "Apple").best("фззду")?.key)
        assertEquals("greeting", index("greeting" to "Привет").best("ghbdtn")?.key)
    }

    @Test
    fun `an empty layout set turns the fix off and fuzzy never uses layouts`() {
        assertNull(food.best("z,kjrj", 0.7, emptyList()))
        assertTrue(food.suggest("z,kjrj", 5, emptyList()).isEmpty())
        assertTrue(food.fuzzy("z,kjrj").isEmpty())
        assertEquals("item.apple", food.best("z,kjrj", 0.7, KeyboardLayout.builtIn)?.key)
    }

    @Test
    fun `a custom layout passed to a call is used and the defaults ignore it`() {
        val dvorak = KeyboardLayout.of("dvorak", "',.pyfgcrl/=\\aoeuidhtns-;qjkxbmwv`[")
        val apples = index("item.apple" to "Apple")
        assertTrue(apples.suggest("arrpd").isEmpty())
        assertEquals("item.apple", apples.suggest("arrpd", 5, KeyboardLayout.builtIn + dvorak).firstOrNull()?.key)
        assertEquals("item.apple", apples.best("arrpd", 0.7, KeyboardLayout.builtIn + dvorak)?.key)
    }

    @Test
    fun `a match of the text as typed beats an equal match after a layout fix`() {
        val entries = index("a.russian" to "Привет", "z.typed" to "ghbdtn")
        assertEquals("z.typed", entries.best("ghbdtn")?.key)
        assertEquals(listOf("z.typed", "a.russian"), entries.suggest("ghbdtn").map { it.key })
    }
}
