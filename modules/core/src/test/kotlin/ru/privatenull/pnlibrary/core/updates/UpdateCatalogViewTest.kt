package ru.privatenull.pnlibrary.core.updates

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.updates.ProductChange
import ru.privatenull.pnlibrary.api.updates.ProductId
import ru.privatenull.pnlibrary.api.updates.ReleaseSummary
import ru.privatenull.pnlibrary.api.updates.UpdateActor
import ru.privatenull.pnlibrary.api.updates.UpdateBackup
import ru.privatenull.pnlibrary.api.updates.UpdateBackupComponent
import ru.privatenull.pnlibrary.api.updates.UpdateChannel
import ru.privatenull.pnlibrary.api.updates.UpdateHistoryEntry
import ru.privatenull.pnlibrary.api.updates.UpdateSnapshot
import ru.privatenull.pnlibrary.api.updates.UpdateState
import ru.privatenull.pnlibrary.api.version.SemanticVersion
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class UpdateCatalogViewTest {
    private val bukkit = UpdateCatalogRoutes("/pn updates", update = { "/pn update $it" }, check = "/pn check")
    private val proxy = UpdateCatalogRoutes("/pnupdate")
    private val now = Instant.parse("2026-10-17T12:00:00Z")

    @Test
    fun `catalog title counts plugins and updates and offers an update button per available plugin`() {
        val lines = UpdateCatalogView.catalog(
            listOf(
                snapshot("pnlibrary", UpdateState.UPDATE_AVAILABLE, "https://github.com/example/repo/blob/main/notes.md"),
                snapshot("currency", UpdateState.UP_TO_DATE),
            ),
            1, false, bukkit,
        )
        val text = lines.joinToString("\n", transform = ::plain)
        assertTrue(plain(lines.first()).startsWith("✦ Каталог  2 плагина · 1 к обновлению"))
        assertTrue(text.contains("pnlibrary 1.0.0 [↑ 1.1.0]"))
        val actions = lines.flatMap(::clicks)
        assertTrue(actions.contains("/pn update pnlibrary"))
        assertTrue(actions.contains("/pn updates info pnlibrary"))
        assertTrue(actions.contains("https://github.com/example/repo/blob/main/notes.md"))
        assertTrue(actions.containsAll(listOf("/pn check", "/pn updates available", "/pn updates history", "/pn updates backups")))
        assertFalse(plain(lines.first { plain(it).contains("currency") }).contains("[↑"))
    }

    @Test
    fun `without an update route the available version is shown as plain text`() {
        val lines = UpdateCatalogView.catalog(listOf(snapshot("pnlibrary", UpdateState.UPDATE_AVAILABLE)), 1, false, proxy)
        assertTrue(lines.joinToString("\n", transform = ::plain).contains("pnlibrary 1.0.0 → 1.1.0"))
        assertFalse(lines.flatMap(::clicks).any { it.startsWith("/pn update ") })
    }

    @Test
    fun `catalog paginates and the available filter excludes current components`() {
        val snapshots = (1..12).map { snapshot("module-$it", UpdateState.UPDATE_AVAILABLE) } + snapshot("current", UpdateState.UP_TO_DATE)
        val first = UpdateCatalogView.catalog(snapshots, 1, true, proxy)
        val second = UpdateCatalogView.catalog(snapshots, 2, true, proxy)
        assertEquals(10, first.count { plain(it).trimStart().startsWith("● module-") })
        assertEquals(2, second.count { plain(it).trimStart().startsWith("● module-") })
        assertFalse(first.any { plain(it).contains("current") })
        assertTrue(first.joinToString("\n", transform = ::plain).contains("страница 1 из 2"))
        assertTrue(first.flatMap(::clicks).contains("/pnupdate available 2"))
        assertTrue(second.flatMap(::clicks).contains("/pnupdate available 1"))
        assertTrue(UpdateCatalogView.catalog(snapshots, Int.MAX_VALUE, false, proxy).joinToString("\n", transform = ::plain)
            .contains("Нет компонентов на этой странице"))
    }

    @Test
    fun `only valid HTTPS changelog URLs become clickable`() {
        val safe = UpdateCatalogView.catalog(
            listOf(snapshot("safe", UpdateState.UPDATE_AVAILABLE, "https://github.com/example/repo/blob/main/notes.md")), 1, false, proxy,
        )
        assertTrue(safe.flatMap(::clicks).any { it.startsWith("https://github.com/") })
        for (url in listOf("javascript:alert(1)", "file:///secret", "https://user:password@example.com/notes")) {
            val unsafe = UpdateCatalogView.catalog(listOf(snapshot("unsafe", UpdateState.UPDATE_AVAILABLE, url)), 1, false, proxy)
            assertFalse(unsafe.flatMap(::clicks).contains(url))
        }
    }

    @Test
    fun `history groups events by day and shows the version transition, outcome and actor`() {
        val today = event("2026-10-17T09:05:00Z", UpdateState.UPDATE_STAGED, "STAGE", "Admin", "pnlibrary", "1.0.0", "1.1.0")
        val yesterday = event("2026-10-16T18:00:00Z", UpdateState.UP_TO_DATE, "HEALTH", "System", "currency", "2.0.0", "2.1.0")
        val older = event("2026-09-12T10:00:00Z", UpdateState.FAILED, "STAGE", "Admin", "pnlibrary", "0.9.0", "1.0.0")
        val lines = UpdateCatalogView.history(listOf(today, yesterday, older), 1, bukkit, totalPages = 1, now = now, zone = ZoneOffset.UTC)
        val text = lines.joinToString("\n", transform = ::plain)

        assertTrue(plain(lines.first()).startsWith("✦ История"))
        assertTrue(text.indexOf("Сегодня") < text.indexOf("Вчера"))
        assertTrue(text.indexOf("Вчера") < text.indexOf("12 сентября"))
        assertTrue(text.contains("09:05 pnlibrary 1.0.0 → 1.1.0 подготовлено к перезапуску · Admin"))
        assertTrue(text.contains("currency 2.0.0 → 2.1.0 применено после перезапуска · System"))
        assertTrue(text.contains("pnlibrary 0.9.0 → 1.0.0 ошибка"))
        assertFalse(text.contains("страница 1 из 1 [Старее]"))
    }

    @Test
    fun `history lines offer a per plugin filter and the filtered view does not`() {
        val entry = event("2026-10-17T09:05:00Z", UpdateState.UPDATE_STAGED, "STAGE", "Admin", "pnlibrary", "1.0.0", "1.1.0")
        val all = UpdateCatalogView.history(listOf(entry), 1, bukkit, totalPages = 1, now = now, zone = ZoneOffset.UTC)
        assertTrue(all.flatMap(::clicks).contains("/pn updates history plugin pnlibrary"))
        assertTrue(hover(all.first { plain(it).contains("pnlibrary 1.0.0") }).contains("/pn updates history plugin pnlibrary"))

        val filtered = UpdateCatalogView.history(listOf(entry), 1, bukkit, totalPages = 1, product = "pnlibrary", now = now, zone = ZoneOffset.UTC)
        assertTrue(plain(filtered.first()).contains("pnlibrary"))
        assertFalse(filtered.first { plain(it).contains("1.0.0 → 1.1.0") }.clickEvent() != null)
    }

    @Test
    fun `history navigation reports page numbers and links to older and newer pages`() {
        val entry = event("2026-10-17T09:05:00Z", UpdateState.UPDATE_STAGED, "STAGE", "Admin", "pnlibrary", "1.0.0", "1.1.0")
        val middle = UpdateCatalogView.history(listOf(entry), 2, bukkit, totalPages = 4, now = now, zone = ZoneOffset.UTC)
        assertTrue(middle.joinToString("\n", transform = ::plain).contains("страница 2 из 4"))
        assertTrue(middle.flatMap(::clicks).containsAll(listOf("/pn updates history 1", "/pn updates history 3", "/pn updates list")))

        val last = UpdateCatalogView.history(listOf(entry), 4, bukkit, totalPages = 4, now = now, zone = ZoneOffset.UTC)
        assertFalse(last.flatMap(::clicks).contains("/pn updates history 5"))

        val filtered = UpdateCatalogView.history(listOf(entry), 1, bukkit, totalPages = 2, product = "pnlibrary", now = now, zone = ZoneOffset.UTC)
        assertTrue(filtered.flatMap(::clicks).contains("/pn updates history plugin pnlibrary 2"))
    }

    @Test
    fun `verified rollback is distinguished from a merely prepared rollback`() {
        val verified = event("2026-10-17T09:05:00Z", UpdateState.ROLLED_BACK, "HEALTH_ROLLBACK", "System", "pnlibrary", "2.0.0", "1.0.0")
        val prepared = event("2026-10-17T08:05:00Z", UpdateState.ROLLED_BACK, "ROLLBACK", "Admin", "pnlibrary", "2.0.0", "1.0.0")
        val text = UpdateCatalogView.history(listOf(verified, prepared), 1, bukkit, totalPages = 1, now = now, zone = ZoneOffset.UTC)
            .joinToString("\n", transform = ::plain)
        assertTrue(text.contains("откат к 2.0.0 откат подтверждён после перезапуска"))
        assertTrue(text.contains("откат к 2.0.0 откат подготовлен к перезапуску"))
    }

    @Test
    fun `an event without version changes shows its message as plain text`() {
        val entry = UpdateHistoryEntry(UUID.randomUUID(), Instant.parse("2026-10-17T09:05:00Z"), UUID.randomUUID(),
            UpdateState.FAILED, UpdateActor.SYSTEM, "CHECK", emptyList(), "<red>HTTP 404</red>")
        val line = UpdateCatalogView.history(listOf(entry), 1, bukkit, totalPages = 1, now = now, zone = ZoneOffset.UTC)
            .first { plain(it).contains("HTTP 404") }
        assertTrue(plain(line).contains("<red>HTTP 404</red>"))
        assertTrue(plain(line).contains("ошибка"))
    }

    @Test
    fun `component events say what was installed, removed, changed by hand or reconfigured`() {
        fun component(operation: String, from: String?, to: String, message: String? = null) = UpdateHistoryEntry(
            UUID.randomUUID(), Instant.parse("2026-10-17T09:05:00Z"), UUID.randomUUID(), UpdateState.UP_TO_DATE,
            UpdateActor.SYSTEM, operation, listOf(ProductChange(ProductId.of("shop"), from?.let(SemanticVersion::parse), SemanticVersion.parse(to))),
            message,
        )
        val text = UpdateCatalogView.history(
            listOf(
                component(ComponentLedger.OPERATION_ADDED, null, "1.0.0"),
                component(ComponentLedger.OPERATION_REMOVED, "1.0.0", "1.0.0"),
                component(ComponentLedger.OPERATION_VERSION_CHANGED, "1.0.0", "1.1.0"),
                component(ComponentLedger.OPERATION_SETTINGS_CHANGED, "1.0.0", "1.0.0", "канал: stable → beta"),
            ),
            1, bukkit, totalPages = 1, now = now, zone = ZoneOffset.UTC,
        ).joinToString("\n", transform = ::plain)

        assertTrue(text.contains("shop 1.0.0 установлен"))
        assertTrue(text.contains("shop 1.0.0 удалён"))
        assertTrue(text.contains("shop 1.0.0 → 1.1.0 версия изменена вручную"))
        assertTrue(text.contains("shop 1.0.0 канал: stable → beta"))
    }

    @Test
    fun `catalog title names the library channel and lines mark channels other than stable`() {
        val lines = UpdateCatalogView.catalog(
            listOf(
                snapshot("pnlibrary", UpdateState.UP_TO_DATE, channel = UpdateChannel.BETA),
                snapshot("shop", UpdateState.UP_TO_DATE),
            ),
            1, false, bukkit,
        )
        assertTrue(plain(lines.first()).contains("pnLibrary: beta"))
        assertTrue(plain(lines.first { plain(it).contains("pnlibrary 1.0.0") }).contains("beta"))
        assertFalse(plain(lines.first { plain(it).contains("shop 1.0.0") }).contains("stable"))
    }

    @Test
    fun `settings card offers every channel, automatic download and holding as buttons except the current choice`() {
        val lines = UpdateCatalogView.settings(snapshot("pnlibrary", UpdateState.UP_TO_DATE, channel = UpdateChannel.BETA), bukkit)
        val text = lines.joinToString("\n", transform = ::plain)
        assertTrue(text.contains("pnlibrary  настройки"))
        assertTrue(text.contains("Канал: ○ stable  ○ rc  ● beta  ○ alpha  ○ dev"))
        assertTrue(text.contains("Автозагрузка: ○ вкл  ● выкл"))
        assertTrue(text.contains("Пауза: 1 ч  1 д  3 д  7 д  30 д"))

        val actions = lines.flatMap(::clicks)
        assertTrue(actions.containsAll(listOf(
            "/pn updates set pnlibrary channel stable", "/pn updates set pnlibrary channel alpha",
            "/pn updates set pnlibrary auto on", "/pn updates set pnlibrary hold 3d", "/pn updates set pnlibrary hold 30d",
        )))
        assertFalse(actions.contains("/pn updates set pnlibrary channel beta"))
        assertFalse(actions.contains("/pn updates set pnlibrary auto off"))
        assertFalse(actions.contains("/pn updates set pnlibrary hold off"))
    }

    @Test
    fun `a reader without edit rights sees the choices but cannot click them`() {
        val lines = UpdateCatalogView.settings(snapshot("pnlibrary", UpdateState.UP_TO_DATE), bukkit.copy(editable = false))
        assertFalse(lines.flatMap(::clicks).any { it.contains(" set ") })
        assertTrue(lines.joinToString("\n", transform = ::plain).contains("Канал: ● stable  ○ rc  ○ beta  ○ alpha  ○ dev"))
    }

    @Test
    fun `a reader that cannot click gets text and a list of commands instead of buttons`() {
        val text = bukkit.copy(interactive = false)
        val pages = listOf(
            UpdateCatalogView.catalog(listOf(snapshot("pnlibrary", UpdateState.UPDATE_AVAILABLE, "https://github.com/a/b")), 1, false, text),
            UpdateCatalogView.info(snapshot("pnlibrary", UpdateState.UPDATE_AVAILABLE), text),
            UpdateCatalogView.settings(snapshot("pnlibrary", UpdateState.UP_TO_DATE), text),
            UpdateCatalogView.history(listOf(event("2026-10-17T09:05:00Z", UpdateState.UPDATE_STAGED, "STAGE", "Admin", "pnlibrary", "1.0.0", "1.1.0")),
                1, text, totalPages = 2, now = now, zone = ZoneOffset.UTC),
        )
        pages.forEach { page -> assertTrue(page.flatMap(::clicks).isEmpty(), page.joinToString("\n", transform = ::plain)) }
        assertTrue(pages[0].joinToString("\n", transform = ::plain).contains("Команды: /pn check"))
        assertTrue(pages[0].joinToString("\n", transform = ::plain).contains("pnlibrary 1.0.0 → 1.1.0"))
        assertTrue(pages[2].joinToString("\n", transform = ::plain).contains("/pn updates set pnlibrary channel <stable|rc|beta|alpha|dev>"))
        assertTrue(pages[3].joinToString("\n", transform = ::plain).contains("следующая: /pn updates history 2"))
    }

    @Test
    fun `the console variant avoids characters outside ASCII`() {
        val ascii = proxy.copy(interactive = false, ascii = true)
        val all = UpdateCatalogView.catalog(listOf(snapshot("pnlibrary", UpdateState.UPDATE_AVAILABLE)), 1, false, ascii) +
            UpdateCatalogView.history(listOf(event("2026-10-17T09:05:00Z", UpdateState.UPDATE_STAGED, "STAGE", "Admin", "pnlibrary", "1.0.0", "1.1.0")),
                1, ascii, totalPages = 1, now = now, zone = ZoneOffset.UTC)
        val text = all.joinToString("\n", transform = ::plain)
        assertTrue(text.contains("pnlibrary 1.0.0 -> 1.1.0"))
        assertFalse(text.any { it in "→↑·●○▬✦" }, text)
    }

    @Test
    fun `titles use RGB colors that legacy clients receive as the nearest standard color`() {
        val title = UpdateCatalogView.catalog(listOf(snapshot("pnlibrary", UpdateState.UP_TO_DATE)), 1, false, bukkit).first()
        val letters = title.children().take(3)
        assertTrue(letters.none { it.hasDecoration(net.kyori.adventure.text.format.TextDecoration.BOLD) })
        assertEquals(0x8B5CF6, letters.first().color()?.value())
        assertTrue(letters.map { it.color()?.value() }.distinct().size > 1)
        assertTrue(letters.none { it.color() is net.kyori.adventure.text.format.NamedTextColor })

        val legacy = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(title)
        assertFalse(legacy.contains("§x"), legacy)
        assertTrue(legacy.startsWith("§"), legacy)
    }

    @Test
    fun `backups show age and open separate confirmation flows rather than executing a restore`() {
        val id = UUID.randomUUID()
        val backup = UpdateBackup(id, now.minusSeconds(4L * 86_400), UUID.randomUUID(),
            listOf(UpdateBackupComponent(ProductId.of("pnlibrary"), "2.0.0", "1.0.0")), 2)
        val lines = UpdateCatalogView.backups(listOf(backup), 1, bukkit, now = now, zone = ZoneOffset.UTC)
        val actions = lines.flatMap(::clicks)
        assertTrue(plain(lines.first()).startsWith("✦ Резервы  1 набор"))
        assertTrue(lines.joinToString("\n", transform = ::plain).contains("pnlibrary 1.0.0 4 д назад"))
        assertTrue(actions.contains("/pn updates rollback $id"))
        assertTrue(actions.contains("/pn updates rollback $id configs"))
        assertFalse(actions.any { it.endsWith(" confirm") })
    }

    @Test
    fun `info card lists the details and offers an update only when one is available`() {
        val available = UpdateCatalogView.info(
            snapshot("pnlibrary", UpdateState.UPDATE_AVAILABLE, "https://github.com/example/repo/blob/main/notes.md"), bukkit,
        )
        val text = available.joinToString("\n", transform = ::plain)
        assertTrue(text.startsWith("✦ pnlibrary  доступно обновление"))
        assertTrue(text.contains("Версия: 1.0.0"))
        assertTrue(text.contains("Доступна: 1.1.0 [Изменения]"))
        assertTrue(text.contains("Канал: stable"))
        assertTrue(text.contains("Автозагрузка: выключена"))
        assertTrue(available.flatMap(::clicks).containsAll(listOf("/pn update pnlibrary", "/pn updates history plugin pnlibrary", "/pn updates list")))

        val current = UpdateCatalogView.info(snapshot("pnlibrary", UpdateState.UP_TO_DATE), bukkit)
        assertFalse(current.flatMap(::clicks).contains("/pn update pnlibrary"))
    }

    @Test
    fun `a paused component shows when the pause ends and offers to lift or extend it`() {
        val until = now.plusSeconds(3L * 86_400)
        val lines = UpdateCatalogView.settings(snapshot("pnlibrary", UpdateState.FROZEN), bukkit, heldUntil = until, now = now, zone = ZoneOffset.UTC)
        val text = lines.joinToString("\n", transform = ::plain)
        assertTrue(text.contains("Пауза: до 20 окт. 12:00 (ещё 3 д) [Снять]") || text.contains("Пауза: до 20 окт 12:00 (ещё 3 д) [Снять]"), text)
        assertTrue(lines.flatMap(::clicks).containsAll(listOf("/pn updates set pnlibrary hold off", "/pn updates set pnlibrary hold 7d")))

        val info = UpdateCatalogView.info(snapshot("pnlibrary", UpdateState.FROZEN), bukkit, heldUntil = until, now = now, zone = ZoneOffset.UTC)
        assertTrue(info.joinToString("\n", transform = ::plain).contains("(ещё 3 д)"))
    }

    @Test
    fun `info card lists the newest release of every channel and switches channel on click`() {
        val releases = listOf(
            ReleaseSummary("1.1.0", UpdateChannel.STABLE, Instant.parse("2026-10-01T00:00:00Z")),
            ReleaseSummary("1.0.5", UpdateChannel.STABLE, Instant.parse("2026-09-01T00:00:00Z")),
            ReleaseSummary("1.2.0-beta.2", UpdateChannel.BETA, Instant.parse("2026-10-10T00:00:00Z")),
        )
        val snapshot = UpdateSnapshot("pnlibrary", "1.0.0", "1.1.0", UpdateChannel.STABLE, UpdateState.UPDATE_AVAILABLE,
            17, 8, false, null, null, availableReleases = releases)
        val lines = UpdateCatalogView.info(snapshot, bukkit)
        assertTrue(lines.joinToString("\n", transform = ::plain).contains("Релизы: stable 1.1.0  beta 1.2.0-beta.2"))
        val actions = lines.flatMap(::clicks)
        assertTrue(actions.contains("/pn updates set pnlibrary channel beta"))
        assertFalse(actions.contains("/pn updates set pnlibrary channel stable"))
    }

    private fun event(at: String, state: UpdateState, operation: String, actor: String, product: String, from: String, to: String) =
        UpdateHistoryEntry(UUID.randomUUID(), Instant.parse(at), UUID.randomUUID(), state,
            UpdateActor(UpdateActor.Kind.CONSOLE, actor.lowercase(), actor), operation,
            listOf(ProductChange(ProductId.of(product), SemanticVersion.parse(from), SemanticVersion.parse(to))), null)

    private fun snapshot(product: String, state: UpdateState, changelog: String? = null, channel: UpdateChannel = UpdateChannel.STABLE) =
        UpdateSnapshot(product, "1.0.0", "1.1.0", channel, state, 17, 8, false, null, null, changelog)

    private fun plain(component: Component) = PlainTextComponentSerializer.plainText().serialize(component)
    private fun clicks(component: Component): List<String> = listOfNotNull(component.clickEvent()?.value()) + component.children().flatMap(::clicks)
    private fun hover(component: Component): String =
        (component.hoverEvent()?.value() as? Component)?.let(::plain).orEmpty().also {
            assertTrue(component.hoverEvent()?.action() == HoverEvent.Action.SHOW_TEXT)
        }
}
