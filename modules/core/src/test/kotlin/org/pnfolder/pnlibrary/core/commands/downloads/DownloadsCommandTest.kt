package org.pnfolder.pnlibrary.core.commands.downloads

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.pnfolder.pnlibrary.api.downloads.DownloadRegistration
import org.pnfolder.pnlibrary.api.downloads.DownloadSnapshot
import org.pnfolder.pnlibrary.api.downloads.DownloadState
import org.pnfolder.pnlibrary.api.plugin.ModuleContext
import org.pnfolder.pnlibrary.api.plugin.ModuleId
import org.pnfolder.pnlibrary.api.plugin.PluginId
import org.pnfolder.pnlibrary.api.plugin.PluginRegistration
import org.pnfolder.pnlibrary.api.plugin.PluginRegistry

class DownloadsCommandTest {
    @Test
    fun `downloads of every module are listed with who asked and the state`() {
        val geoip = mock(DownloadRegistration::class.java)
        `when`(geoip.snapshots()).thenReturn(listOf(DownloadSnapshot("geoip", DownloadState.STAGED), DownloadSnapshot("skins", DownloadState.FAILED, "нет сети")))
        val module = mock(ModuleContext::class.java)
        `when`(module.id).thenReturn(ModuleId.of("main"))
        `when`(module.downloads).thenReturn(geoip)
        val plain = mock(ModuleContext::class.java)
        `when`(plain.id).thenReturn(ModuleId.of("plain"))
        `when`(plain.downloads).thenReturn(null)
        val registration = mock(PluginRegistration::class.java)
        `when`(registration.id).thenReturn(PluginId.of("shop"))
        `when`(registration.all()).thenReturn(listOf(module, plain))
        val registry = mock(PluginRegistry::class.java)
        `when`(registry.all()).thenReturn(listOf(registration))

        val all = declaredDownloads(registry)

        assertEquals(listOf("shop/main" to "geoip", "shop/main" to "skins"), all.map { it.owner to it.snapshot.key })
        assertEquals("нет сети", all.last().snapshot.message)
    }
}
