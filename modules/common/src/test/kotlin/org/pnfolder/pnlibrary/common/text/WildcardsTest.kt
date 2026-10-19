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
