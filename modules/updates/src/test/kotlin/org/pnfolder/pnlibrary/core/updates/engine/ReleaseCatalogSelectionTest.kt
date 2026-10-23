package org.pnfolder.pnlibrary.core.updates.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.platform.PlatformType
import org.pnfolder.pnlibrary.api.updates.UpdateChannel

class ReleaseCatalogSelectionTest {
    private val catalog = ReleaseCatalogCodec().decode(
        requireNotNull(javaClass.getResourceAsStream("/catalog/releases.json")).readBytes(),
    )

    @Test
    fun `stable ignores newer beta and dev releases`() {
        val release = ReleaseCatalogSelector.select(catalog, UpdateChannel.STABLE, PlatformType.BUKKIT, 21, 1)
        assertEquals("2.5.1", release!!.version.toString())
    }

    @Test
    fun `beta selects newest stable or beta release`() {
        val release = ReleaseCatalogSelector.select(catalog, UpdateChannel.BETA, PlatformType.BUKKIT, 21, 1)
        assertEquals("2.6.0-beta.40", release!!.version.toString())
    }
}
