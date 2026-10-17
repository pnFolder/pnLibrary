package ru.privatenull.pnlibrary.core.updates

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.privatenull.pnlibrary.api.updates.UpdateChannel
import java.nio.file.Files
import java.nio.file.Path

class UpdatePolicyOverridesTest {
    @TempDir
    lateinit var directory: Path

    private val file get() = directory.resolve("updates/settings.json")

    @Test
    fun `nothing is overridden until a choice is made`() {
        val overrides = UpdatePolicyOverrides(file)
        assertNull(overrides.channel("pnlibrary"))
        assertNull(overrides.automatic("pnlibrary"))
        assertFalse(overrides.anyAutomaticEnabled())
        assertFalse(Files.exists(file))
    }

    @Test
    fun `choices apply immediately and survive reopening`() {
        val overrides = UpdatePolicyOverrides(file)
        overrides.setChannel("pnlibrary", UpdateChannel.BETA)
        overrides.setAutomatic("pnlibrary", true)
        overrides.setAutomatic("shop", false)

        assertEquals(UpdateChannel.BETA, overrides.channel("pnlibrary"))
        assertTrue(overrides.anyAutomaticEnabled())

        val reopened = UpdatePolicyOverrides(file)
        assertEquals(UpdateChannel.BETA, reopened.channel("pnlibrary"))
        assertEquals(true, reopened.automatic("pnlibrary"))
        assertEquals(false, reopened.automatic("shop"))
        assertNull(reopened.channel("shop"))
    }

    @Test
    fun `a later choice replaces the earlier one and turning everything off clears the flag`() {
        val overrides = UpdatePolicyOverrides(file)
        overrides.setAutomatic("pnlibrary", true)
        overrides.setAutomatic("pnlibrary", false)
        overrides.setChannel("pnlibrary", UpdateChannel.RC)
        overrides.setChannel("pnlibrary", UpdateChannel.STABLE)

        assertFalse(overrides.anyAutomaticEnabled())
        assertEquals(UpdateChannel.STABLE, overrides.channel("pnlibrary"))
    }

    @Test
    fun `unreadable or invalid content is ignored with a warning`() {
        Files.createDirectories(file.parent)
        Files.writeString(file, "{ not json")
        val warnings = mutableListOf<String>()
        assertNull(UpdatePolicyOverrides(file, warnings::add).channel("pnlibrary"))
        assertEquals(1, warnings.size)

        Files.writeString(file, """{"channels":{"pnlibrary":"nightly","shop":"BETA"},"automatic":{}}""")
        val tolerant = UpdatePolicyOverrides(file)
        assertNull(tolerant.channel("pnlibrary"))
        assertEquals(UpdateChannel.BETA, tolerant.channel("shop"))
    }
}
