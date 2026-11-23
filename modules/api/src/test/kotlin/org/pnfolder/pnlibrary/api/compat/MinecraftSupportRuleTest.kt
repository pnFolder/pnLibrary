package org.pnfolder.pnlibrary.api.compat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion

class MinecraftSupportRuleTest {
    private fun support(rule: MinecraftSupportRule, version: String?) = rule.evaluate(version?.let(MinecraftVersion::parseInfo))

    @Test
    fun `nothing declared accepts every version`() {
        assertEquals(MinecraftSupport.NOT_DECLARED, support(MinecraftSupportRule(), "1.21.11"))
    }

    @Test
    fun `a range includes both ends and tells older from newer`() {
        val rule = MinecraftSupportRule().apply { between("1.16.5", "1.21.4") }

        assertEquals(MinecraftSupport.SUPPORTED, support(rule, "1.16.5"))
        assertEquals(MinecraftSupport.SUPPORTED, support(rule, "1.18.2"))
        assertEquals(MinecraftSupport.SUPPORTED, support(rule, "1.21.4"))
        assertEquals(MinecraftSupport.OLDER_THAN_DECLARED, support(rule, "1.16.4"))
        assertEquals(MinecraftSupport.NEWER_THAN_DECLARED, support(rule, "1.21.5"))
    }

    @Test
    fun `from accepts a release the library has never heard of`() {
        val rule = MinecraftSupportRule().apply { between("1.16.5", null) }

        assertEquals(MinecraftSupport.SUPPORTED, support(rule, "1.99.3"))
        assertEquals(MinecraftSupport.SUPPORTED, support(rule, "2.0"))
        assertEquals(MinecraftSupport.OLDER_THAN_DECLARED, support(rule, "1.8.8"))
    }

    @Test
    fun `only the listed versions pass and the gaps between them are excluded`() {
        val rule = MinecraftSupportRule().apply { between("1.20.1", "1.20.1"); between("1.20.4", "1.20.4") }

        assertEquals(MinecraftSupport.SUPPORTED, support(rule, "1.20.4"))
        assertEquals(MinecraftSupport.EXCLUDED, support(rule, "1.20.2"))
        assertEquals(MinecraftSupport.NEWER_THAN_DECLARED, support(rule, "1.21"))
    }

    @Test
    fun `a version that cannot be read is unknown and the description lists what was declared`() {
        val rule = MinecraftSupportRule().apply { between("1.16.5", "1.21.4"); between("1.8.8", "1.8.8") }

        assertEquals(MinecraftSupport.UNKNOWN, support(rule, null))
        assertEquals(MinecraftSupport.UNKNOWN, support(rule, "Paper"))
        assertEquals("1.16.5-1.21.4, 1.8.8", rule.description)
    }

    @Test
    fun `a range that ends before it starts and a text that is no version are refused`() {
        assertThrows(IllegalArgumentException::class.java) { MinecraftSupportRule().between("1.21", "1.16") }
        assertThrows(IllegalArgumentException::class.java) { MinecraftSupportRule().between("latest", null) }
    }
}
