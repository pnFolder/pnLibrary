package org.pnfolder.pnlibrary.core.placeholders

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PlaceholderAddressTest {
    @Test
    fun `parses plugin module and name`() {
        assertEquals(PlaceholderAddress("pnclans", "war", "score"), PlaceholderAddress.parse("pnclans/war:score"))
    }

    @Test
    fun `shortened forms are not addresses`() {
        listOf("pnclans:score", "pnclans/war", "pnclans/war:", "/war:score", "pnclans/:score", "a/b/c:d", "").forEach {
            assertNull(PlaceholderAddress.parse(it), it)
        }
    }

    @Test
    fun `an address prints the way it is parsed`() {
        assertEquals("pnclans/main:score", PlaceholderAddress("pnclans", PlaceholderAddress.DEFAULT_MODULE, "score").toString())
    }

    @Test
    fun `placeholder api form uses the prefix and a dot between module and name`() {
        val address = PlaceholderAddress("pnclans", "war", "score")
        assertEquals("pnclans_war.score", address.forPlaceholderApi())
        assertEquals("clans_war.score", address.forPlaceholderApi("clans"))
    }

    @Test
    fun `a plugin id with an underscore is rejected because placeholder api splits on it`() {
        assertThrows(IllegalArgumentException::class.java) { PlaceholderAddress("pn_clans", "war", "score") }
    }

    @Test
    fun `separators are not allowed inside a part`() {
        assertThrows(IllegalArgumentException::class.java) { PlaceholderAddress("pnclans", "wa:r", "score") }
        assertThrows(IllegalArgumentException::class.java) { PlaceholderAddress("pnclans", "war", "sc/ore") }
    }
}
