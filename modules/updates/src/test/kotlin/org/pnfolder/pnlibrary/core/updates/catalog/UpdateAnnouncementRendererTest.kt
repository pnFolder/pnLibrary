package org.pnfolder.pnlibrary.core.updates.catalog

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.platform.PlatformType
import org.pnfolder.pnlibrary.api.updates.ProductId
import org.pnfolder.pnlibrary.api.updates.UpdateChannel
import org.pnfolder.pnlibrary.api.updates.UpdatePlanSnapshot
import org.pnfolder.pnlibrary.api.updates.UpdateState
import org.pnfolder.pnlibrary.api.updates.release.ArtifactDescriptor
import org.pnfolder.pnlibrary.api.updates.release.ProductChange
import org.pnfolder.pnlibrary.api.updates.release.ProductRelease
import org.pnfolder.pnlibrary.api.updates.release.UpdatePlan
import org.pnfolder.pnlibrary.api.version.ApiVersionRange
import org.pnfolder.pnlibrary.api.version.SemanticVersion
import java.net.URI
import java.util.UUID

class UpdateAnnouncementRendererTest {
    private companion object {
        val PLAIN = org.pnfolder.pnlibrary.console.ConsoleTheme("", "", "", "", "")
    }

    @Test
    fun `available update uses the console card design`() {
        val product = ProductId.of("pndemo")
        val from = SemanticVersion.parse("2.5.0")
        val to = SemanticVersion.parse("2.6.0-beta.1")
        val artifact = ArtifactDescriptor(
            "pndemo-2.6.0-beta.1-bukkit-java8.jar", PlatformType.BUKKIT, 8, null,
            downloadUri = URI("https://example.test/pndemo.jar"),
        )
        val release = ProductRelease(product, to, UpdateChannel.BETA, ApiVersionRange(1, 1), artifacts = listOf(artifact))
        val plan = UpdatePlan(1, listOf(ProductChange(product, from, to)), listOf(release))
        val lines = UpdateAnnouncementRenderer.render(
            UpdatePlanSnapshot(UUID.randomUUID(), 1, UpdateState.UPDATE_AVAILABLE, plan, emptyList(), null),
            PLAIN,
        )

        assertTrue(lines.any { it.contains("ПРОВЕРКА ОБНОВЛЕНИЙ") })
        assertTrue(lines.any { it.contains("ОБНОВЛЕНИЯ") })
        assertTrue(lines.any { it.contains("Плагин") && it.contains("pndemo") })
        assertTrue(lines.any { it.contains("2.5.0") && it.contains("2.6.0-beta.1") })
        assertTrue(lines.any { it.contains("тестовый канал Beta") })
        assertTrue(lines.any { it.contains("pndemo-2.6.0-beta.1-bukkit-java8.jar") })
        assertTrue(lines.any { it.contains("Автозагрузка выключена") })
    }

    @Test
    fun `failed update explains the result without raw logger output`() {
        val lines = UpdateAnnouncementRenderer.render(
            UpdatePlanSnapshot(UUID.randomUUID(), 2, UpdateState.FAILED, null, emptyList(), "Read timed out"),
            PLAIN,
        )

        assertTrue(lines.any { it.contains("ПРИЧИНА") })
        assertTrue(lines.any { it.contains("Read timed out") })
        assertTrue(lines.any { it.contains("Проверка не выполнена") })
        assertTrue(lines.none { it.startsWith("[pnLibrary]") })
    }
}
