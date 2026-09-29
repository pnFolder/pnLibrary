package ru.privatenull.pnlibrary.update

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.api.updates.UpdateChannel

class ReleaseCatalogCodecTest {
    private val codec = ReleaseCatalogCodec()

    @Test
    fun `decodes complete bukkit release without hashes`() {
        val catalog = codec.decode("""
            {"schema":1,"product":"pnlibrary","releases":[{
              "version":"2.6.0-beta.3","channel":"beta","description":"Test",
              "publishedAt":"2026-09-29T12:00:00Z",
              "api":{"minimum":1,"maximum":1},"artifacts":[{
                "file":"pnLibrary-2.6.0-beta.3-bukkit-java8.jar","platform":"BUKKIT",
                "minecraft":{"minimum":"1.20","maximum":"1.21.11"},
                "java":{"minimum":8,"maximum":21},"url":"https://example.org/a.jar"
              }]}]}
        """.trimIndent().toByteArray())

        val release = catalog.releases.single()
        assertEquals("pnlibrary", catalog.product)
        assertEquals("2.6.0-beta.3", release.version.toString())
        assertEquals(UpdateChannel.BETA, release.channel)
        assertEquals(PlatformType.BUKKIT, release.artifacts.single().platform)
        assertEquals("1.20", release.artifacts.single().minecraft!!.minimum)
    }

    @Test
    fun `rejects explicit null optional field`() {
        assertThrows<ManifestException> {
            codec.decode("""
                {"schema":1,"product":"demo","releases":[{"version":"1.0.0",
                "channel":"stable","publishedAt":"2026-09-29T12:00:00Z",
                "api":{"minimum":1,"maximum":1},"artifacts":[{
                "file":"demo.jar","platform":"VELOCITY","platformApi":null,
                "java":{"minimum":17},"url":"https://example.org/demo.jar"}]}]}
            """.toByteArray())
        }
    }
}
