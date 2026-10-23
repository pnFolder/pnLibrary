package org.pnfolder.pnlibrary.localization.layout

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayout.Companion.BE
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayout.Companion.CS
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayout.Companion.DE
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayout.Companion.EN_US
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayout.Companion.ES
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayout.Companion.FR
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayout.Companion.GREEK
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayout.Companion.HE
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayout.Companion.IT
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayout.Companion.PT_BR
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayout.Companion.RU
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayout.Companion.TR_Q
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayout.Companion.UK

class KeyboardLayoutTest {
    private val letters = mapOf(
        EN_US to "qwertyuiopasdfghjklzxcvbnm",
        RU to "йцукенгшщзхъфывапролджэячсмитьбюё",
        UK to "йцукенгшщзхїґфівапролджєячсмитьбю",
        BE to "йцукенгшўзфывапролджэячсмітьбюё",
        DE to "qwertzuiopüasdfghjklöäyxcvbnmß",
        FR to "azertyuiopqsdfghjklmùwxcvbn",
        ES to "qwertyuiopçasdfghjklñzxcvbnm",
        IT to "qwertyuiopèùasdfghjklòàzxcvbnm",
        PT_BR to "qwertyuiopasdfghjklçzxcvbnm",
        TR_Q to "qwertyuıopğüasdfghjklşizxcvbnmöç",
        CS to "qwertzuiopúasdfghjklůyxcvbnm",
        GREEK to "ςερτυθιοπασδφγηξκλζχψωβνμ",
        HE to "קראטוןםפשדגכעיחלךףזסבהנמצתץ",
    )

    private val dvorak = KeyboardLayout.of("dvorak", "',.pyfgcrl/=\\aoeuidhtns-;qjkxbmwv`[")

    @Test
    fun `every pair of built-in layouts round trips`() {
        for ((from, sample) in letters) for (to in KeyboardLayout.builtIn) {
            assertEquals(sample, KeyboardLayout.convert(KeyboardLayout.convert(sample, from, to), to, from), "$from -> $to")
        }
    }

    @Test
    fun `known words convert in both directions`() {
        assertEquals("яблоко", KeyboardLayout.convert("z,kjrj", EN_US, RU))
        assertEquals("apple", KeyboardLayout.convert("фззду", RU, EN_US))
        assertEquals("привет", KeyboardLayout.convert("ghbdtn", EN_US, RU))
        assertEquals("qwerty", KeyboardLayout.convert("azerty", FR, EN_US))
        assertEquals("qwerty", KeyboardLayout.convert("qwertz", DE, EN_US))
        assertEquals("іїєґ", KeyboardLayout.convert("s]'\\", EN_US, UK))
        assertEquals("s]'\\", KeyboardLayout.convert("іїєґ", UK, EN_US))
    }

    @Test
    fun `one known word per added layout converts from US keys`() {
        assertEquals("вада", KeyboardLayout.convert("dflf", EN_US, BE))
        assertEquals("ναι", KeyboardLayout.convert("nai", EN_US, GREEK))
        assertEquals("שלום", KeyboardLayout.convert("akuo", EN_US, HE))
        assertEquals("año", KeyboardLayout.convert("a;o", EN_US, ES))
        assertEquals("più", KeyboardLayout.convert("pi\\", EN_US, IT))
        assertEquals("şeker", KeyboardLayout.convert(";eker", EN_US, TR_Q))
        assertEquals("faça", KeyboardLayout.convert("fa;a", EN_US, PT_BR))
        assertEquals("můj", KeyboardLayout.convert("m;j", EN_US, CS))
        assertEquals("§", KeyboardLayout.convert("'", EN_US, CS))
    }

    @Test
    fun `case is kept and other characters pass through`() {
        assertEquals("Яблоко", KeyboardLayout.convert("Z,kjrj", EN_US, RU))
        assertEquals("ЯбЛОКО", KeyboardLayout.convert("Z,KJRJ", EN_US, RU))
        assertEquals("яблоко 123!", KeyboardLayout.convert("z,kjrj 123!", EN_US, RU))
    }

    @Test
    fun `detect finds the layouts that hold every letter`() {
        val latin = KeyboardLayout.detect("hello")
        assertEquals(EN_US, latin.first())
        assertTrue(latin.containsAll(listOf(DE, FR, ES, IT, PT_BR, TR_Q, CS)))
        assertFalse(GREEK in latin || HE in latin)
        assertEquals(listOf(RU, UK), KeyboardLayout.detect("привет"))
        assertEquals(listOf(RU, BE), KeyboardLayout.detect("ты"))
        assertEquals(listOf(UK), KeyboardLayout.detect("їжак"))
        assertEquals(listOf(DE), KeyboardLayout.detect("straße"))
        assertEquals(listOf(GREEK), KeyboardLayout.detect("ναι"))
        assertEquals(listOf(HE), KeyboardLayout.detect("שלום"))
        assertTrue(KeyboardLayout.detect("яблоko").isEmpty())
        assertTrue(KeyboardLayout.detect("123 !").isEmpty())
    }

    @Test
    fun `alternatives are ordered, distinct and never the text itself`() {
        val alternatives = KeyboardLayout.alternatives("z,kjrj")
        assertEquals("яблоко", alternatives.first())
        assertFalse("z,kjrj" in alternatives)
        assertEquals(alternatives.distinct(), alternatives)
        assertEquals(alternatives, KeyboardLayout.alternatives("z,kjrj"))
        assertTrue(KeyboardLayout.alternatives("123").isEmpty())
    }

    @Test
    fun `alternatives of a converted word lead back to the original`() {
        assertTrue("z,kjrj" in KeyboardLayout.alternatives("яблоко"))
        assertTrue("apple" in KeyboardLayout.alternatives("фззду"))
    }

    @Test
    fun `built-in layouts keep a stable order`() {
        assertEquals(
            listOf("en_us", "ru", "uk", "be", "de", "fr", "es", "it", "pt_br", "tr_q", "cs", "el", "he"),
            KeyboardLayout.builtIn.map { it.id },
        )
    }

    @Test
    fun `layouts are equal by id and keys`() {
        val keys = "qwertyuiop[]\\asdfghjkl;'zxcvbnm,.`-"
        assertEquals(EN_US, KeyboardLayout.of("en_us", keys))
        assertEquals(EN_US.hashCode(), KeyboardLayout.of("en_us", keys).hashCode())
        assertNotEquals(EN_US, KeyboardLayout.of("other", keys))
        assertNotEquals(EN_US, RU)
        assertEquals("ru", RU.toString())
    }

    @Test
    fun `of rejects a blank id, a wrong key count and repeated keys`() {
        val keys = "qwertyuiop[]\\asdfghjkl;'zxcvbnm,.`-"
        assertThrows(IllegalArgumentException::class.java) { KeyboardLayout.of(" ", keys) }
        assertThrows(IllegalArgumentException::class.java) { KeyboardLayout.of("short", keys.dropLast(1)) }
        assertThrows(IllegalArgumentException::class.java) { KeyboardLayout.of("long", keys + "a") }
        val repeated = assertThrows(IllegalArgumentException::class.java) { KeyboardLayout.of("twice", "q".repeat(35)) }
        assertTrue(repeated.message!!.contains("twice"))
    }

    @Test
    fun `a custom layout converts and extends the set of a call`() {
        val sample = letters.getValue(EN_US)
        assertEquals(sample, KeyboardLayout.convert(KeyboardLayout.convert(sample, EN_US, dvorak), dvorak, EN_US))
        assertEquals("apple", KeyboardLayout.convert("arrpd", EN_US, dvorak))
        assertFalse("apple" in KeyboardLayout.alternatives("arrpd"))
        assertTrue("apple" in KeyboardLayout.alternatives("arrpd", KeyboardLayout.builtIn + dvorak))
        assertEquals("apple", KeyboardLayout.alternatives("arrpd", listOf(EN_US, dvorak)).first())
        assertTrue(KeyboardLayout.alternatives("arrpd", emptyList()).isEmpty())
    }
}
