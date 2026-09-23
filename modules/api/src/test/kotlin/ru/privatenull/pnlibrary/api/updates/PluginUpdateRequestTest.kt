package ru.privatenull.pnlibrary.api.updates

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.version.ApiVersionRange
import ru.privatenull.pnlibrary.api.version.PnLibraryApi
import ru.privatenull.pnlibrary.api.platform.PlatformType

class PluginUpdateRequestTest {
    @Test
    fun `update declaration describes repository API and platform artifacts`() {
        val request = PluginUpdateRequest.builder()
            .repository("pnFolder", "pnCases")
            .apiVersions(1, 2)
            .artifact("(?i)^pnCases-Bukkit-.*\\.jar$", PlatformType.BUKKIT, 17)
            .build()

        assertEquals(ApiVersionRange(1, 2), request.supportedApi)
        assertEquals(PlatformType.BUKKIT, request.artifacts.single().platform)
    }

    @Test
    fun `legacy declarations receive resolver compatible API defaults`() {
        val request = PluginUpdateRequest.builder()
            .repository("pnFolder", "PnMarket")
            .artifactPattern("(?i)^pnmarket-.*\\.jar$")
            .build()

        assertTrue(request.supportedApi.supports(PnLibraryApi.VERSION))
    }

    @Test
    fun `declares exact artifact and API range`() {
        val request = PluginUpdateRequest.builder()
            .repository("pnFolder", "PnMarket")
            .supportedApi(3, 5)
            .exactArtifact("pnMarket-4.0.0+api5.jar", 17)
            .build()

        assertEquals(ApiVersionRange(3, 5), request.supportedApi)
        assertTrue(Regex(request.artifacts.single().pattern).matches("pnMarket-4.0.0+api5.jar"))
        assertFalse(Regex(request.artifacts.single().pattern).matches("prefix-pnMarket-4.0.0+api5.jar"))
    }
}
