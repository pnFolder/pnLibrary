package ru.privatenull.pnlibrary.update

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.api.updates.*
import ru.privatenull.pnlibrary.api.version.ApiVersionRange
import ru.privatenull.pnlibrary.api.version.SemanticVersion

class ProductReleaseCodecTest {
    private val codec = ProductReleaseCodec()

    @Test
    fun `round trip preserves product and platform compatibility without dependencies`() {
        val manifest = ProductReleaseManifest(
            ProductId.of("pnauth"), "pnAuth", SemanticVersion.parse("2.4.0"), UpdateChannel.STABLE,
            listOf(ProductArtifact(
                "pnAuth-paper-2.4.0.jar",
                ArtifactCompatibility(
                    PlatformType.BUKKIT,
                    VersionRangeText("1.20.4", "1.21.4"),
                    VersionRangeText("1.20.4-R0.1", null),
                    ApiVersionRange(1, 2),
                    17,
                    21,
                ),
                128,
                "a".repeat(64),
            )),
        )

        val encoded = codec.encode(manifest)
        assertEquals(manifest, codec.decode(encoded))
        assertFalse(encoded.toString(Charsets.UTF_8).contains("dependencies"))
    }

    @Test
    fun `rejects dependency policy in release manifest`() {
        val json = validJson().replace("\"artifacts\"", "\"dependencies\":[],\"artifacts\"")
        assertThrows(IllegalArgumentException::class.java) { codec.decode(json.toByteArray()) }
    }

    @Test
    fun `rejects unsafe artifact filename`() {
        val json = validJson().replace("pnauth.jar", "../pnauth.jar")
        assertThrows(IllegalArgumentException::class.java) { codec.decode(json.toByteArray()) }
    }

    private fun validJson() = """{
      "schema":1,"productId":"pnauth","displayName":"pnAuth","version":"2.4.0","channel":"stable",
      "artifacts":[{"file":"pnauth.jar","platform":"BUKKIT","pnLibraryApi":{"minimum":1,"maximum":2},
      "java":{"minimum":17,"maximum":21},"size":128,"sha256":"${"a".repeat(64)}"}]
    }"""
}
