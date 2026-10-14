package ru.privatenull.pnlibrary.update

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.api.updates.UpdateChannel

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
