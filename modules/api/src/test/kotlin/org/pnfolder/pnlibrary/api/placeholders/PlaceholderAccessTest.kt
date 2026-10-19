package org.pnfolder.pnlibrary.api.placeholders

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.plugin.PluginId

class PlaceholderAccessTest {
    @Test
    fun `patterns are trimmed and matched case-insensitively`() {
        val access = PlaceholderAccess.builder().allowMatching("  admin-*  ").deny("admin-untrusted").build()
        val owner = PluginId.of("owner")

        assertEquals(setOf("admin-*"), access.allowedPatterns)
        assertTrue(access.allows(owner, PluginId.of("admin-tools")))
        assertFalse(access.allows(owner, PluginId.of("admin-untrusted")))
        assertFalse(access.allows(owner, PluginId.of("scoreboard")))
    }

    @Test
    fun `blank patterns are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { PlaceholderAccess.builder().allowMatching(" ") }
    }
}
