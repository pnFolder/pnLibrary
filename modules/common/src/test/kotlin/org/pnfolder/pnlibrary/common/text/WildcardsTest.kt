package org.pnfolder.pnlibrary.common.text

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WildcardsTest {
    @Test
    fun `star matches any sequence and case is ignored`() {
        assertTrue(Wildcards.matches("shop-*", "Shop-Main"))
        assertTrue(Wildcards.matches("shop-*", "shop-"))
        assertTrue(Wildcards.matches("*-addon", "economy-addon"))
        assertTrue(Wildcards.matches("a*b*c", "aXXbYYc"))
        assertFalse(Wildcards.matches("shop-*", "myshop-main"))
    }

    @Test
    fun `other characters are literal`() {
        assertTrue(Wildcards.matches("clan.v2", "clan.v2"))
        assertFalse(Wildcards.matches("clan.v2", "clanXv2"))
        assertFalse(Wildcards.matches("clan?", "clans"))
        assertTrue(Wildcards.matches("exact", "EXACT"))
        assertFalse(Wildcards.matches("exact", "exactly"))
    }
}

class WildcardsCacheTest {
    private val patterns = listOf("*", "", "shop-*", "*-addon", "a*b*c", "clan.v2", "Exact", "x*", "a\\Qb*", "[a-z]*", "(?i)q", "pn*lib*", "**", "é*")
    private val values = listOf("", "shop-main", "SHOP-", "eco-addon", "aXbYc", "clan.v2", "clanXv2", "EXACT",
        "xyz", "a\\Qbzz", "abc", "q", "pnlibrary", "PN-lib", "Éa", "[a-z]x")

    private fun reference(pattern: String, value: String): Boolean {
        val expression = pattern.split('*').joinToString(".*", transform = java.util.regex.Pattern::quote)
        return java.util.regex.Pattern.compile("^$expression$", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(value).matches()
    }

    @Test
    fun `cached matching equals compiling on every call`() {
        for (pattern in patterns) for (value in values) {
            org.junit.jupiter.api.Assertions.assertEquals(reference(pattern, value), Wildcards.matches(pattern, value), "$pattern / $value")
        }
    }

    @Test
    fun `a pattern is compiled once however many times it is used`() {
        val unique = List(7) { "cache-test-$it-*" }
        val before = Wildcards.compilationCount()
        repeat(100_000) { Wildcards.matches(unique[it % unique.size], "cache-test-${it % 7}-value") }
        org.junit.jupiter.api.Assertions.assertEquals(unique.size, Wildcards.compilationCount() - before)
    }
}
