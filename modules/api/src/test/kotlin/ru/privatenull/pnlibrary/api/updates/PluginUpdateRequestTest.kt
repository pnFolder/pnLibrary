package ru.privatenull.pnlibrary.api.updates

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.version.ApiVersionRange
import ru.privatenull.pnlibrary.api.version.PnLibraryApi
import ru.privatenull.pnlibrary.api.version.SemanticVersion
import ru.privatenull.pnlibrary.api.platform.PlatformType

class PluginUpdateRequestTest {
    @Test
    fun `single updates declaration describes API platform and dependencies`() {
        val vault = ExternalPluginDependency.builder("Vault", "1.7.3")
            .downloadPage("https://github.com/MilkBowl/Vault/releases").build()
        val request = PluginUpdateRequest.builder()
            .repository("pnFolder", "pnCases")
            .apiVersions(1, 2)
            .managedDependency("pneconomy", "2.0.0", "pnFolder", "pnEconomy")
            .pluginDependency(vault)
            .artifact("(?i)^pnCases-Bukkit-.*\\.jar$", PlatformType.BUKKIT, 17)
            .build()

        assertEquals(ApiVersionRange(1, 2), request.supportedApi)
        assertEquals("pneconomy", request.managedProductDependencies.single().product.value)
        assertEquals("Vault", request.externalPluginDependencies.single().plugin)
        assertEquals(PlatformType.BUKKIT, request.artifacts.single().platform)
    }

    @Test
    fun `legacy declarations receive resolver compatible API defaults`() {
        val request = PluginUpdateRequest.builder()
            .repository("pnFolder", "PnMarket")
            .artifactPattern("(?i)^pnmarket-.*\\.jar$")
            .build()

        assertTrue(request.supportedApi.supports(PnLibraryApi.VERSION))
        assertTrue(request.dependencies.isEmpty())
    }

    @Test
    fun `declares exact artifact API range and product dependencies`() {
        val request = PluginUpdateRequest.builder()
            .repository("pnFolder", "PnMarket")
            .supportedApi(3, 5)
            .exactArtifact("pnMarket-4.0.0+api5.jar", 17)
            .dependsOn("economy", "2.1.0")
            .build()

        assertEquals(ApiVersionRange(3, 5), request.supportedApi)
        assertEquals(
            ProductDependency(ProductId.of("economy"), SemanticVersion.parse("2.1.0")),
            request.dependencies.single(),
        )
        assertTrue(Regex(request.artifacts.single().pattern).matches("pnMarket-4.0.0+api5.jar"))
        assertFalse(Regex(request.artifacts.single().pattern).matches("prefix-pnMarket-4.0.0+api5.jar"))
    }
}
