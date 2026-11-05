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
    fun `placeholder api form uses the given prefix and a tilde between module and name`() {
        val address = PlaceholderAddress("pnclans", "war", "score")
        assertEquals("clans_war~score", address.forPlaceholderApi("clans"))
    }

    @Test
    fun `a plugin id with an underscore is rejected because placeholder api splits on it`() {
        assertThrows(IllegalArgumentException::class.java) { PlaceholderAddress("pn_clans", "war", "score") }
    }

    @Test
    fun `a name may be a pattern with dots and braces`() {
        assertEquals("pnclans/war:player.{name}.kills", PlaceholderAddress.parse("pnclans/war:player.{name}.kills").toString())
    }

    @Test
    fun `of gives null instead of failing for a plugin id with an underscore`() {
        assertNull(PlaceholderAddress.of("pn_clans", "war", "score"))
    }

    @Test
    fun `separators are not allowed inside a part`() {
        assertThrows(IllegalArgumentException::class.java) { PlaceholderAddress("pnclans", "wa:r", "score") }
        assertThrows(IllegalArgumentException::class.java) { PlaceholderAddress("pnclans", "war", "sc/ore") }
    }
}
