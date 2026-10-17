package ru.privatenull.pnlibrary.core.updates

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.updates.*
import ru.privatenull.pnlibrary.api.version.SemanticVersion
import java.time.Instant
import java.util.UUID

class UpdateCatalogViewTest {
    @Test
    fun `Bukkit catalog includes navigation and an update action without losing the changelog link`() {
        val lines = UpdateCatalogView.catalog(listOf(snapshot("pnlibrary", UpdateState.UPDATE_AVAILABLE,
            "https://github.com/example/repo/blob/main/notes.md")), 1, false, "/pn updates list")
        val actions = lines.flatMap(::clicks)
        assertTrue(actions.contains("/pn update pnlibrary"))
        assertTrue(actions.containsAll(listOf("/pn updates available", "/pn updates history", "/pn updates backups", "/pn check")))
        assertTrue(actions.contains("https://github.com/example/repo/blob/main/notes.md"))
        assertTrue(lines.joinToString("\n", transform = ::plain).contains("1 к обновлению"))
    }

    @Test
    fun `backup buttons open separate confirmation flows rather than executing a restore`() {
        val id = UUID.randomUUID()
        val backup = UpdateBackup(id, Instant.EPOCH, UUID.randomUUID(),
            listOf(UpdateBackupComponent(ProductId.of("pnlibrary"), "2.0.0", "1.0.0")), 2)
        val actions = UpdateCatalogView.backups(listOf(backup), 1, "/pn updates backups").flatMap(::clicks)
        assertTrue(actions.contains("/pn updates rollback $id"))
        assertTrue(actions.contains("/pn updates rollback $id configs"))
        assertFalse(actions.any { it.endsWith(" confirm") })
    }

    @Test
    fun `catalog pages and available filter do not include current components`() {
        val snapshots = (1..12).map { snapshot("module-$it", UpdateState.UPDATE_AVAILABLE) } + snapshot("current", UpdateState.UP_TO_DATE)
        val first = UpdateCatalogView.catalog(snapshots, 1, true, "/pnupdate available")
        val second = UpdateCatalogView.catalog(snapshots, 2, true, "/pnupdate available")
        assertEquals(12, first.size)
        assertEquals(4, second.size)
        assertFalse(first.any { plain(it).contains("current") })
        assertTrue(first.flatMap(::clicks).contains("/pnupdate available 2"))
        assertTrue(second.flatMap(::clicks).contains("/pnupdate available 1"))
        assertTrue(plain(UpdateCatalogView.catalog(snapshots, Int.MAX_VALUE, false, "/pnupdate list")[1]).contains("Нет компонентов"))
    }

    @Test
    fun `only valid HTTPS changelog URLs become clickable`() {
        val safe = UpdateCatalogView.catalog(listOf(snapshot("safe", UpdateState.UPDATE_AVAILABLE, "https://github.com/example/repo/blob/main/notes.md")), 1, false, "/pnupdate list")
        assertTrue(safe.flatMap(::clicks).any { it.startsWith("https://github.com/") })
        for (url in listOf("javascript:alert(1)", "file:///secret", "https://user:password@example.com/notes")) {
            assertFalse(UpdateCatalogView.catalog(listOf(snapshot("unsafe", UpdateState.UPDATE_AVAILABLE, url)), 1, false, "/pnupdate list").flatMap(::clicks).contains(url))
        }
    }

    @Test
    fun `history shows event time actor and preparation rather than implying running installation`() {
        val entry = UpdateHistoryEntry(UUID.randomUUID(), Instant.parse("2026-10-17T12:30:00Z"), UUID.randomUUID(),
            UpdateState.UPDATE_STAGED, UpdateActor(UpdateActor.Kind.CONSOLE, "console", "Admin"), "STAGE", emptyList(), "ready")
        val lines = UpdateCatalogView.history(listOf(entry), 1, "/pn updates history")
        val text = lines.joinToString("\n", transform = ::plain)
        assertTrue(text.contains("2026-10-17 12:30:00 UTC"))
        assertTrue(text.contains("Admin"))
        assertTrue(text.contains("подготовлено к перезапуску"))
        assertFalse(text.contains("применено"))
    }

    @Test
    fun `verified rollback is distinguished from a merely prepared rollback`() {
        val entry = UpdateHistoryEntry(UUID.randomUUID(), Instant.EPOCH, UUID.randomUUID(), UpdateState.ROLLED_BACK,
            UpdateActor.SYSTEM, "HEALTH_ROLLBACK", listOf(ProductChange(ProductId.of("pnlibrary"),
                SemanticVersion.parse("2.0.0"),
                SemanticVersion.parse("1.0.0"))), null)
        val text = UpdateCatalogView.history(listOf(entry), 1, "/pn updates history").joinToString("\n", transform = ::plain)
        assertTrue(text.contains("откат подтверждён после перезапуска"))
        assertTrue(text.contains("2.0.0 → 1.0.0"))
        assertFalse(text.contains("подготовлен"))
    }

    private fun snapshot(product: String, state: UpdateState, changelog: String? = null) =
        UpdateSnapshot(product, "1.0.0", "1.1.0", UpdateChannel.STABLE, state, 17, 8, false, null, null, changelog)

    private fun plain(component: Component) = PlainTextComponentSerializer.plainText().serialize(component)
    private fun clicks(component: Component): List<String> = listOfNotNull(component.clickEvent()?.value()) + component.children().flatMap(::clicks)
}
