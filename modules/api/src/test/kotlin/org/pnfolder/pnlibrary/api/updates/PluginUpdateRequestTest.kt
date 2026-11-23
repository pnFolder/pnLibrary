package org.pnfolder.pnlibrary.api.updates

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.platform.PlatformType
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel
import org.pnfolder.pnlibrary.api.updates.request.PluginUpdateRequest
import org.pnfolder.pnlibrary.api.version.ApiVersionRange
import org.pnfolder.pnlibrary.api.version.PnLibraryApi

class PluginUpdateRequestTest {
    @Test
    fun `update declaration describes repository API and platform artifacts`() {
        val request = PluginUpdateRequest.builder()
            .repository("pnFolder", "pnCases")
            .supportedApi(1, 2)
            .artifact("(?i)^pnCases-Bukkit-.*\\.jar$", PlatformType.BUKKIT, 17)
            .build()

        assertEquals(ApiVersionRange(1, 2), request.supportedApi)
        assertEquals(PlatformType.BUKKIT, request.artifacts.single().platform)
    }

    @Test
    fun `a plugin with one jar needs no artifact rule`() {
        val request = PluginUpdateRequest.builder().repository("pnFolder", "pnCases").build()

        assertTrue(request.artifacts.isEmpty())
    }

    @Test
    fun `channel defaults to stable and and automatic download is on until an administrator changes them`() {
        fun request(configure: PluginUpdateRequest.Builder.() -> Unit) = PluginUpdateRequest.builder()
            .repository("pnFolder", "pnCases")
            .exactArtifact("pnCases.jar")
            .apply(configure)
            .build()

        assertEquals(UpdateChannel.STABLE, request {}.channel)
    }

    @Test
    fun `legacy declarations receive resolver compatible API defaults`() {
        val request = PluginUpdateRequest.builder()
            .repository("pnFolder", "PnMarket")
            .artifact("(?i)^pnmarket-.*\\.jar$", 8)
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
