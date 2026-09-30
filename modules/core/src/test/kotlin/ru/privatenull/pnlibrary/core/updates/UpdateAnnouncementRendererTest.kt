package ru.privatenull.pnlibrary.core.updates

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue
import ru.privatenull.pnlibrary.api.updates.ProductChange
import ru.privatenull.pnlibrary.api.updates.ProductId
import ru.privatenull.pnlibrary.api.updates.ProductRelease
import ru.privatenull.pnlibrary.api.updates.UpdateChannel
import ru.privatenull.pnlibrary.api.updates.UpdatePlan
import ru.privatenull.pnlibrary.api.updates.UpdatePlanSnapshot
import ru.privatenull.pnlibrary.api.updates.UpdateState
import ru.privatenull.pnlibrary.api.updates.ArtifactDescriptor
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.api.version.ApiVersionRange
import ru.privatenull.pnlibrary.api.version.SemanticVersion
import java.util.UUID
import java.net.URI

class UpdateAnnouncementRendererTest {
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
        )

        assertTrue(lines.any { it.contains("ПРИЧИНА") })
        assertTrue(lines.any { it.contains("Read timed out") })
        assertTrue(lines.any { it.contains("Проверка не выполнена") })
        assertTrue(lines.none { it.startsWith("[pnLibrary]") })
    }
}
