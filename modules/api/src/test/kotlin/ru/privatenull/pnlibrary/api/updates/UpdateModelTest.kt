package ru.privatenull.pnlibrary.api.updates

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.api.version.ApiVersionRange
import ru.privatenull.pnlibrary.api.version.SemanticVersion

class UpdateModelTest {
    @Test
    fun `channels express the maximum accepted risk`() {
        assertTrue(UpdateChannel.STABLE.accepts(UpdateChannel.STABLE))
        assertFalse(UpdateChannel.STABLE.accepts(UpdateChannel.BETA))
        assertTrue(UpdateChannel.BETA.accepts(UpdateChannel.STABLE))
        assertTrue(UpdateChannel.ALPHA.accepts(UpdateChannel.BETA))
        assertTrue(UpdateChannel.DEV.accepts(UpdateChannel.DEV))
    }

    @Test
    fun `component identifiers are normalized and validated`() {
        assertEquals("pnmarket", ProductId.of("PnMarket").value)
        assertThrows(IllegalArgumentException::class.java) { ProductId.of("bad id") }
    }

    @Test
    fun `release validates API and dependency metadata`() {
        val dependency = ProductDependency(
            ProductId.of("pneconomy"),
            SemanticVersion.parse("3.0.0"),
        )
        val release = ProductRelease(
            product = ProductId.of("pnmarket"),
            version = SemanticVersion.parse("4.0.0"),
            channel = UpdateChannel.STABLE,
            supportedApi = ApiVersionRange(5, 5),
            dependencies = listOf(dependency),
        )

        assertEquals(5, release.supportedApi.minimum)
        assertEquals("pneconomy", release.dependencies.single().product.value)
    }

    @Test
    fun `product descriptor validates and preserves product metadata`() {
        assertThrows(IllegalArgumentException::class.java) {
            ProductDescriptor.builder("economy", "3.4.0").pnLibraryApi(2, 1)
        }
        val descriptor = ProductDescriptor.builder("economy", "3.4.0")
            .pnLibraryApi(1, 2)
            .build()
        assertEquals("economy", descriptor.id.value)
        assertEquals("3.4.0", descriptor.version.toString())
    }

    @Test
    fun `artifact descriptor rejects unsafe file and malformed digest`() {
        assertThrows(IllegalArgumentException::class.java) {
            ArtifactDescriptor("../economy.jar", PlatformType.BUKKIT, 8, null, 10, "00".repeat(32), null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ArtifactDescriptor("economy.jar", PlatformType.BUKKIT, 8, null, 10, "00", null)
        }
    }

    @Test
    fun `automatic external dependency requires secure complete artifact`() {
        assertThrows(IllegalArgumentException::class.java) {
            ExternalPluginDependency.builder("Vault", "1.7.3")
                .artifact("http://example.test/vault.jar", 10, "00".repeat(32))
                .build()
        }
        val manual = ExternalPluginDependency.builder("Vault", "1.7.3")
            .downloadPage("https://github.com/MilkBowl/Vault/releases")
            .build()
        assertEquals("Vault", manual.plugin)
        assertEquals(null, manual.artifact)
    }
}
