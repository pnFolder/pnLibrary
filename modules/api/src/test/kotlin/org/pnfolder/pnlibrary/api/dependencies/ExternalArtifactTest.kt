package org.pnfolder.pnlibrary.api.dependencies

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.dependencies.external.ExternalArtifact
import org.pnfolder.pnlibrary.api.dependencies.external.ExternalPluginDependency
import org.pnfolder.pnlibrary.api.platform.PlatformType
import java.net.URI

class ExternalArtifactTest {
    private fun file(vararg extra: Pair<String, Any?>): ExternalArtifact {
        val options = extra.toMap()
        @Suppress("UNCHECKED_CAST")
        return ExternalArtifact(
            URI("https://example.org/x.jar"), null,
            platforms = options["platforms"] as? Set<PlatformType> ?: emptySet(),
            minecraftFrom = options["from"] as? String,
            minecraftBefore = options["before"] as? String,
        )
    }

    @Test
    fun `a file without limits fits every server`() {
        assertTrue(file().fits(PlatformType.VELOCITY, null))
        assertTrue(file().fits(PlatformType.BUKKIT, "1.21.4"))
    }

    @Test
    fun `the minecraft range includes its start and leaves out its end`() {
        val range = file("from" to "1.20", "before" to "1.21")

        assertTrue(range.fits(PlatformType.BUKKIT, "1.20"))
        assertTrue(range.fits(PlatformType.BUKKIT, "1.20.6"))
        assertFalse(range.fits(PlatformType.BUKKIT, "1.21"))
        assertFalse(range.fits(PlatformType.BUKKIT, "1.19.4"))
    }

    @Test
    fun `an unknown minecraft version fits no ranged file`() {
        assertFalse(file("from" to "1.20").fits(PlatformType.BUKKIT, null))
        assertFalse(file("from" to "1.20").fits(PlatformType.VELOCITY, "not a version"))
    }

    @Test
    fun `a platform limit is respected`() {
        val proxy = file("platforms" to setOf(PlatformType.VELOCITY))

        assertTrue(proxy.fits(PlatformType.VELOCITY, null))
        assertFalse(proxy.fits(PlatformType.BUKKIT, "1.21"))
    }

    @Test
    fun `bad input is refused`() {
        assertThrows(IllegalArgumentException::class.java) { ExternalArtifact(URI("http://example.org/x.jar"), null) }
        assertThrows(IllegalArgumentException::class.java) { ExternalArtifact(URI("https://example.org/x.jar"), "short") }
        assertThrows(IllegalArgumentException::class.java) { ExternalArtifact(URI("https://example.org/x.jar"), null, minecraftFrom = "latest") }
        assertThrows(IllegalArgumentException::class.java) { ExternalArtifact(URI("https://example.org/x.jar"), null, maxBytes = 0) }
    }

    @Test
    fun `the first fitting variant wins and the general file is the fallback`() {
        val dependency = ExternalPluginDependency.builder("Vault", "1.7.0")
            .variant("https://example.org/old.jar", minecraftBefore = "1.20")
            .variant("https://example.org/new.jar", minecraftFrom = "1.20")
            .url("https://example.org/general.jar")
            .build()

        assertEquals("old.jar", dependency.artifactFor(PlatformType.BUKKIT, "1.16.5")?.uri?.path?.substringAfterLast('/'))
        assertEquals("new.jar", dependency.artifactFor(PlatformType.BUKKIT, "1.21.4")?.uri?.path?.substringAfterLast('/'))
        assertEquals("general.jar", dependency.artifactFor(PlatformType.VELOCITY, null)?.uri?.path?.substringAfterLast('/'))
        assertEquals("general.jar", dependency.artifact?.uri?.path?.substringAfterLast('/'))
    }
}
