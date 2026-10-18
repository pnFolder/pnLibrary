package ru.privatenull.pnlibrary.core.updates

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
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
    fun `catalog header counts plugins and updates and offers an update button per available plugin`() {
        val lines = catalog(
            listOf(
                snapshot("pnlibrary", UpdateState.UPDATE_AVAILABLE, "https://github.com/example/repo/blob/main/notes.md"),
                snapshot("currency", UpdateState.UP_TO_DATE),
            ),
            routes = bukkit,
        )
        val text = lines.joinToString("\n", transform = ::plain)
        assertTrue(plain(lines[0]).startsWith("── ✦ Каталог плагинов ─"), plain(lines[0]))
        assertEquals("  2 плагина · 1 обновление", plain(lines[1]))
        assertTrue(text.contains("■ pnlibrary v1.0.0 » v1.1.0 [Обновить]"), text)
        assertTrue(text.contains("■ currency v1.0.0"), text)
        val actions = lines.flatMap(::clicks)
        assertTrue(actions.contains("/pn update pnlibrary"))
        assertTrue(actions.contains("/pn updates info pnlibrary"))
        assertTrue(actions.contains("https://github.com/example/repo/blob/main/notes.md"))
        assertTrue(actions.containsAll(listOf("/pn check", "/pn updates available", "/pn updates history", "/pn updates backups")))
        assertFalse(plain(lines.first { plain(it).contains("currency") }).contains("[Обновить]"))
    }

    @Test
    fun `a catalog row opens the plugin card and explains itself in a multi-line hover`() {
        val releases = listOf(ReleaseSummary("1.1.0", UpdateChannel.STABLE, Instant.parse("2026-10-01T00:00:00Z")))
        val snapshot = UpdateSnapshot("pnlibrary", "1.0.0", "1.1.0", UpdateChannel.STABLE, UpdateState.UPDATE_AVAILABLE,
            17, 8, true, null, "Каталог ответил", availableReleases = releases)
        val row = catalog(listOf(snapshot), routes = bukkit).first { plain(it).contains("pnlibrary") }
        assertEquals("/pn updates info pnlibrary", row.clickEvent()?.value())
        val card = hover(row)
        assertTrue(card.startsWith("pnlibrary\n"), card)
        assertTrue(card.contains("Статус: доступно обновление"))
        assertTrue(card.contains("Версия: v1.0.0 » v1.1.0"))
        assertTrue(card.contains("Выпущена: 1 октября · 16 д назад"))
        assertTrue(card.contains("Автозагрузка: включена"))
        assertTrue(card.contains("Каталог ответил"))
    }

    @Test
    fun `the summary reports prepared updates and failures and reads as all clear otherwise`() {
        val busy = catalog(listOf(
            snapshot("a", UpdateState.UPDATE_STAGED), snapshot("b", UpdateState.FAILED), snapshot("c", UpdateState.INCOMPATIBLE),
        ), routes = proxy)
        assertEquals("  3 плагина · 1 к перезапуску · 2 ошибки", plain(busy[1]))
        assertTrue(plain(busy.first { plain(it).contains(" a ") }).contains("v1.0.0 » v1.1.0 · ждёт перезапуска"))

        val calm = catalog(listOf(snapshot("a", UpdateState.UP_TO_DATE)), routes = proxy)
        assertEquals("  1 плагин · √ всё актуально", plain(calm[1]))
        val none = catalog(listOf(snapshot("a", UpdateState.UP_TO_DATE)), updatesOnly = true, routes = proxy)
        assertTrue(none.any { plain(it) == "  √ Все плагины актуальны" })
    }

    @Test
    fun `without an update route the available version is shown as plain text`() {
        val lines = catalog(listOf(snapshot("pnlibrary", UpdateState.UPDATE_AVAILABLE)), routes = proxy)
        assertTrue(lines.joinToString("\n", transform = ::plain).contains("pnlibrary v1.0.0 » v1.1.0"))
        assertFalse(lines.flatMap(::clicks).any { it.startsWith("/pn update ") })
    }

    @Test
    fun `catalog paginates and the available filter excludes current components`() {
        val snapshots = (1..12).map { snapshot("module-$it", UpdateState.UPDATE_AVAILABLE) } + snapshot("current", UpdateState.UP_TO_DATE)
        val first = catalog(snapshots, 1, true, proxy)
        val second = catalog(snapshots, 2, true, proxy)
        assertEquals(10, first.count { plain(it).trimStart().startsWith("■ module-") })
        assertEquals(2, second.count { plain(it).trimStart().startsWith("■ module-") })
        assertFalse(first.drop(2).any { plain(it).contains("current") })
        assertTrue(first.joinToString("\n", transform = ::plain).contains("страница 1 из 2 [Следующая »]"))
        assertTrue(first.flatMap(::clicks).contains("/pnupdate available 2"))
        assertTrue(second.flatMap(::clicks).contains("/pnupdate available 1"))
        assertTrue(catalog(snapshots, Int.MAX_VALUE, false, proxy).joinToString("\n", transform = ::plain).contains("На этой странице пусто"))
    }

    @Test
    fun `only valid HTTPS changelog URLs become clickable`() {
        val safe = catalog(listOf(snapshot("safe", UpdateState.UPDATE_AVAILABLE, "https://github.com/example/repo/blob/main/notes.md")), routes = proxy)
        assertTrue(safe.flatMap(::clicks).any { it.startsWith("https://github.com/") })
        for (url in listOf("javascript:alert(1)", "file:///secret", "https://user:password@example.com/notes")) {
            val unsafe = catalog(listOf(snapshot("unsafe", UpdateState.UPDATE_AVAILABLE, url)), routes = proxy)
            assertFalse(unsafe.flatMap(::clicks).contains(url))
            assertFalse(unsafe.joinToString("\n", transform = ::plain).contains(url))
        }
    }

    @Test
    fun `history groups events by day and shows the version transition, outcome and actor`() {
        val today = event("2026-10-17T09:05:00Z", UpdateState.UPDATE_STAGED, "STAGE", "Admin", "pnlibrary", "1.0.0", "1.1.0")
        val yesterday = event("2026-10-16T18:00:00Z", UpdateState.UP_TO_DATE, "HEALTH", "System", "currency", "2.0.0", "2.1.0")
        val older = event("2026-09-12T10:00:00Z", UpdateState.FAILED, "STAGE", "Admin", "pnlibrary", "0.9.0", "1.0.0")
        val lines = history(listOf(today, yesterday, older))
        val text = lines.joinToString("\n", transform = ::plain)

        assertTrue(plain(lines.first()).startsWith("── ✦ История ─"))
        assertTrue(text.indexOf("Сегодня") < text.indexOf("Вчера"))
        assertTrue(text.indexOf("Вчера") < text.indexOf("12 сентября"))
        assertTrue(text.contains("■ 09:05 pnlibrary v1.0.0 » v1.1.0 · подготовлено · Admin"), text)
        assertTrue(text.contains("currency v2.0.0 » v2.1.0 · применено · System"))
        assertTrue(text.contains("pnlibrary v0.9.0 » v1.0.0 · ошибка"))
        assertFalse(text.contains("страница 1 из 1"))
        assertTrue(hover(lines.first { plain(it).contains("09:05") }).contains("Итог: подготовлено, применится после перезапуска"))
    }

    @Test
    fun `history lines offer a per plugin filter and the filtered view does not`() {
        val entry = event("2026-10-17T09:05:00Z", UpdateState.UPDATE_STAGED, "STAGE", "Admin", "pnlibrary", "1.0.0", "1.1.0")
        val all = history(listOf(entry))
        assertTrue(all.flatMap(::clicks).contains("/pn updates history plugin pnlibrary"))
        assertTrue(hover(all.first { plain(it).contains("pnlibrary v1.0.0") }).contains("/pn updates history plugin pnlibrary"))

        val filtered = history(listOf(entry), product = "pnlibrary")
        assertTrue(plain(filtered.first()).contains("История · pnlibrary"))
        assertNull(filtered.first { plain(it).contains("v1.0.0 » v1.1.0") }.clickEvent())
        assertTrue(filtered.flatMap(::clicks).containsAll(listOf("/pn updates history", "/pn updates info pnlibrary")))
    }

    @Test
    fun `history navigation reports page numbers and links to older and newer pages`() {
        val entry = event("2026-10-17T09:05:00Z", UpdateState.UPDATE_STAGED, "STAGE", "Admin", "pnlibrary", "1.0.0", "1.1.0")
        val middle = history(listOf(entry), page = 2, totalPages = 4)
        assertTrue(middle.joinToString("\n", transform = ::plain).contains("[« Новее] страница 2 из 4 [Старее »]"))
        assertTrue(middle.flatMap(::clicks).containsAll(listOf("/pn updates history 1", "/pn updates history 3", "/pn updates list")))

        assertFalse(history(listOf(entry), page = 4, totalPages = 4).flatMap(::clicks).contains("/pn updates history 5"))
        assertTrue(history(listOf(entry), totalPages = 2, product = "pnlibrary").flatMap(::clicks).contains("/pn updates history plugin pnlibrary 2"))
        assertTrue(history(emptyList()).any { plain(it) == "  Событий пока нет." })
    }

    @Test
    fun `verified rollback is distinguished from a merely prepared rollback`() {
        val verified = event("2026-10-17T09:05:00Z", UpdateState.ROLLED_BACK, "HEALTH_ROLLBACK", "System", "pnlibrary", "2.0.0", "1.0.0")
        val prepared = event("2026-10-17T08:05:00Z", UpdateState.ROLLED_BACK, "ROLLBACK", "Admin", "pnlibrary", "2.0.0", "1.0.0")
        val text = history(listOf(verified, prepared)).joinToString("\n", transform = ::plain)
        assertTrue(text.contains("откат к v2.0.0 · подтверждён"))
        assertTrue(text.contains("откат к v2.0.0 · ждёт перезапуска"))
    }

    @Test
    fun `an event without version changes shows its message as plain text`() {
        val entry = UpdateHistoryEntry(UUID.randomUUID(), Instant.parse("2026-10-17T09:05:00Z"), UUID.randomUUID(),
            UpdateState.FAILED, UpdateActor.SYSTEM, "CHECK", emptyList(), "<red>HTTP 404</red>")
        val line = history(listOf(entry)).first { plain(it).contains("HTTP 404") }
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
        val text = history(listOf(
            component(ComponentLedger.OPERATION_ADDED, null, "1.0.0"),
            component(ComponentLedger.OPERATION_REMOVED, "1.0.0", "1.0.0"),
            component(ComponentLedger.OPERATION_VERSION_CHANGED, "1.0.0", "1.1.0"),
            component(ComponentLedger.OPERATION_SETTINGS_CHANGED, "1.0.0", "1.0.0", "канал: stable → beta"),
        )).joinToString("\n", transform = ::plain)

        assertTrue(text.contains("shop v1.0.0 · установлен"))
        assertTrue(text.contains("shop v1.0.0 · удалён"))
        assertTrue(text.contains("shop v1.0.0 » v1.1.0 · изменена вручную"))
        assertTrue(text.contains("shop v1.0.0 · канал: stable → beta"))
    }

    @Test
    fun `catalog lines tag channels other than stable`() {
        val lines = catalog(
            listOf(snapshot("pnlibrary", UpdateState.UP_TO_DATE, channel = UpdateChannel.BETA), snapshot("shop", UpdateState.UP_TO_DATE)),
            routes = bukkit,
        )
        assertTrue(plain(lines.first { plain(it).contains("pnlibrary v1.0.0") }).endsWith("· beta"))
        assertFalse(plain(lines.first { plain(it).contains("shop v1.0.0") }).contains("stable"))
    }

    @Test
    fun `settings card offers every channel, automatic download and holding as buttons except the current choice`() {
        val lines = UpdateCatalogView.settings(snapshot("pnlibrary", UpdateState.UP_TO_DATE, channel = UpdateChannel.BETA), bukkit)
        val text = lines.joinToString("\n", transform = ::plain)
        assertTrue(plain(lines.first()).startsWith("── ✦ Настройки · pnlibrary ─"))
        assertTrue(text.contains("Канал: stable  rc  ■ beta  alpha  dev"))
        assertTrue(text.contains("Автозагрузка: вкл  ■ выкл"))
        assertTrue(text.contains("Пауза: нет · 1 ч  1 д  3 д  7 д  30 д"))

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
        val lines = UpdateCatalogView.settings(snapshot("pnlibrary", UpdateState.FROZEN), bukkit.copy(editable = false),
            heldUntil = now.plusSeconds(3600), now = now, zone = ZoneOffset.UTC)
        val text = lines.joinToString("\n", transform = ::plain)
        assertFalse(lines.flatMap(::clicks).any { it.contains(" set ") })
        assertTrue(text.contains("Только просмотр"))
        assertTrue(text.contains("Канал: ■ stable  rc  beta  alpha  dev"))
        assertTrue(text.contains("Пауза: до 17 окт. 13:00 · ещё 1 ч") || text.contains("Пауза: до 17 окт 13:00 · ещё 1 ч"), text)
        assertFalse(text.contains("[Снять]"))
    }

    @Test
    fun `a reader that cannot click gets text and a list of commands instead of buttons`() {
        val text = bukkit.copy(interactive = false)
        val pages = listOf(
            catalog(listOf(snapshot("pnlibrary", UpdateState.UPDATE_AVAILABLE, "https://github.com/a/b")), routes = text),
            UpdateCatalogView.info(snapshot("pnlibrary", UpdateState.UPDATE_AVAILABLE), text),
            UpdateCatalogView.settings(snapshot("pnlibrary", UpdateState.UP_TO_DATE), text),
            history(listOf(event("2026-10-17T09:05:00Z", UpdateState.UPDATE_STAGED, "STAGE", "Admin", "pnlibrary", "1.0.0", "1.1.0")),
                routes = text, totalPages = 2),
        )
        pages.forEach { page -> assertTrue(page.flatMap(::clicks).isEmpty(), page.joinToString("\n", transform = ::plain)) }
        val catalog = pages[0].joinToString("\n", transform = ::plain)
        assertTrue(catalog.contains("  Команды:\n    /pn check · проверить обновления"), catalog)
        assertTrue(catalog.contains("pnlibrary v1.0.0 » v1.1.0\n      изменения: https://github.com/a/b"), catalog)
        assertFalse(catalog.contains("Нажмите"))
        assertTrue(pages[2].joinToString("\n", transform = ::plain).contains("/pn updates set pnlibrary channel <stable|rc|beta|alpha|dev>"))
        assertTrue(pages[3].joinToString("\n", transform = ::plain).contains("следующая: /pn updates history 2"))
    }

    @Test
    fun `the console variant uses only ASCII and Cyrillic`() {
        val ascii = proxy.copy(interactive = false, ascii = true)
        val backup = UpdateBackup(UUID.randomUUID(), now.minusSeconds(3600), UUID.randomUUID(),
            listOf(UpdateBackupComponent(ProductId.of("pnlibrary"), "2.0.0", "1.0.0")), 1)
        val all = catalog(listOf(snapshot("pnlibrary", UpdateState.UPDATE_AVAILABLE), snapshot("shop", UpdateState.UP_TO_DATE)), routes = ascii) +
            UpdateCatalogView.info(snapshot("pnlibrary", UpdateState.FROZEN), ascii, heldUntil = now.plusSeconds(7200), now = now, zone = ZoneOffset.UTC) +
            UpdateCatalogView.settings(snapshot("pnlibrary", UpdateState.UP_TO_DATE), ascii, now = now, zone = ZoneOffset.UTC) +
            history(listOf(event("2026-10-17T09:05:00Z", UpdateState.UPDATE_STAGED, "STAGE", "Admin", "pnlibrary", "1.0.0", "1.1.0")), routes = ascii) +
            UpdateCatalogView.backups(listOf(backup), 1, ascii, now = now, zone = ZoneOffset.UTC)
        val text = all.joinToString("\n", transform = ::plain)
        assertTrue(text.contains("* pnlibrary v1.0.0 -> v1.1.0"), text)
        assertTrue(text.contains("-- * Каталог плагинов --"), text)
        assertTrue(text.all { it.code < 128 || it in 'А'..'я' || it == 'ё' || it == 'Ё' }, text)
    }

    @Test
    fun `titles use RGB colors that legacy clients receive as the nearest standard color`() {
        val title = catalog(listOf(snapshot("pnlibrary", UpdateState.UP_TO_DATE)), routes = bukkit).first()
        val letters = title.children().take(3)
        assertTrue(letters.none { it.hasDecoration(TextDecoration.BOLD) })
        assertEquals(0x8B5CF6, letters.first().color()?.value())
        assertTrue(title.children().map { it.color()?.value() }.distinct().size > 1)
        assertTrue(letters.none { it.color() is NamedTextColor })

        val legacy = LegacyComponentSerializer.legacySection().serialize(title)
        assertFalse(legacy.contains("§x"), legacy)
        assertTrue(legacy.startsWith("§"), legacy)
    }

    @Test
    fun `every palette role keeps its own color on servers without RGB`() {
        val expected = mapOf(
            UpdatePalette.TEXT to NamedTextColor.WHITE, UpdatePalette.SOFT to NamedTextColor.GRAY,
            UpdatePalette.HINT to NamedTextColor.BLUE, UpdatePalette.FAINT to NamedTextColor.DARK_GRAY,
            UpdatePalette.ACCENT to NamedTextColor.LIGHT_PURPLE, UpdatePalette.OK to NamedTextColor.GREEN,
            UpdatePalette.INFO to NamedTextColor.AQUA, UpdatePalette.WARN to NamedTextColor.YELLOW,
            UpdatePalette.ORANGE to NamedTextColor.GOLD, UpdatePalette.DANGER to NamedTextColor.RED,
        )
        expected.forEach { (color, legacy) -> assertEquals(legacy, NamedTextColor.nearestTo(color), color.asHexString()) }
    }

    @Test
    fun `backups show age and open separate confirmation flows rather than executing a restore`() {
        val id = UUID.randomUUID()
        val backup = UpdateBackup(id, now.minusSeconds(4L * 86_400), UUID.randomUUID(),
            listOf(UpdateBackupComponent(ProductId.of("pnlibrary"), "2.0.0", "1.0.0")), 2)
        val lines = UpdateCatalogView.backups(listOf(backup), 1, bukkit, now = now, zone = ZoneOffset.UTC)
        val actions = lines.flatMap(::clicks)
        assertTrue(plain(lines.first()).startsWith("── ✦ Резервы · 1 набор ─"))
        val row = lines.first { plain(it).contains("pnlibrary") }
        assertEquals("  ■ pnlibrary v1.0.0 · 4 д назад [Откат] [+ конфиги]", plain(row))
        assertTrue(hover(row).contains("Вернёт: pnlibrary v1.0.0 (сейчас v2.0.0)"))
        assertTrue(actions.contains("/pn updates rollback $id"))
        assertTrue(actions.contains("/pn updates rollback $id configs"))
        assertFalse(actions.any { it.endsWith(" confirm") })
        assertTrue(UpdateCatalogView.backups(emptyList(), 1, bukkit).any { plain(it).contains("Резервов пока нет") })
    }

    @Test
    fun `info card lists the details and offers an update only when one is available`() {
        val available = UpdateCatalogView.info(
            snapshot("pnlibrary", UpdateState.UPDATE_AVAILABLE, "https://github.com/example/repo/blob/main/notes.md"), bukkit,
        )
        val text = available.joinToString("\n", transform = ::plain)
        assertTrue(text.startsWith("── ✦ pnlibrary ─"))
        assertTrue(text.contains("\n  ■ доступно обновление\n"))
        assertTrue(text.contains("Версия: v1.0.0 » v1.1.0 [Изменения]"))
        assertTrue(text.contains("Канал: stable"))
        assertTrue(text.contains("Автозагрузка: выключена"))
        assertTrue(text.contains("Требует: Java 8+"))
        assertTrue(available.flatMap(::clicks).containsAll(listOf("/pn update pnlibrary", "/pn updates history plugin pnlibrary", "/pn updates list")))

        val current = UpdateCatalogView.info(snapshot("pnlibrary", UpdateState.UP_TO_DATE, latest = "1.0.0"), bukkit)
        assertFalse(current.flatMap(::clicks).contains("/pn update pnlibrary"))
        assertTrue(current.joinToString("\n", transform = ::plain).contains("Версия: v1.0.0\n"))

        val oldJava = UpdateSnapshot("pnlibrary", "1.0.0", null, UpdateChannel.STABLE, UpdateState.INCOMPATIBLE, 8, 17, false, null, null)
        assertTrue(UpdateCatalogView.info(oldJava, bukkit).joinToString("\n", transform = ::plain).contains("Требует: Java 17+ (на сервере 8)"))
    }

    @Test
    fun `a paused component shows when the pause ends and offers to lift or replace it`() {
        val until = now.plusSeconds(3L * 86_400)
        val lines = UpdateCatalogView.settings(snapshot("pnlibrary", UpdateState.FROZEN), bukkit, heldUntil = until, now = now, zone = ZoneOffset.UTC)
        val text = lines.joinToString("\n", transform = ::plain)
        assertTrue(text.contains("Пауза: до 20 окт. 12:00 · ещё 3 д [Снять]") || text.contains("Пауза: до 20 окт 12:00 · ещё 3 д [Снять]"), text)
        assertTrue(text.contains("Новый срок: 1 ч  1 д  3 д  7 д  30 д"))
        assertTrue(lines.flatMap(::clicks).containsAll(listOf("/pn updates set pnlibrary hold off", "/pn updates set pnlibrary hold 7d")))

        val info = UpdateCatalogView.info(snapshot("pnlibrary", UpdateState.FROZEN), bukkit, heldUntil = until, now = now, zone = ZoneOffset.UTC)
        val card = info.joinToString("\n", transform = ::plain)
        assertTrue(card.contains("■ приостановлено"))
        assertTrue(card.contains("· ещё 3 д"))
    }

    @Test
    fun `info card shows the release date and the newest release of every other channel, switching channel on click`() {
        val releases = listOf(
            ReleaseSummary("1.1.0", UpdateChannel.STABLE, Instant.parse("2026-10-01T00:00:00Z")),
            ReleaseSummary("1.0.5", UpdateChannel.STABLE, Instant.parse("2026-09-01T00:00:00Z")),
            ReleaseSummary("1.2.0-beta.2", UpdateChannel.BETA, Instant.parse("2026-10-10T00:00:00Z")),
        )
        val snapshot = UpdateSnapshot("pnlibrary", "1.0.0", "1.1.0", UpdateChannel.STABLE, UpdateState.UPDATE_AVAILABLE,
            17, 8, false, null, null, availableReleases = releases)
        val lines = UpdateCatalogView.info(snapshot, bukkit, now = now, zone = ZoneOffset.UTC)
        val text = lines.joinToString("\n", transform = ::plain)
        assertTrue(text.contains("Выпущена: 1 октября · 16 д назад"), text)
        assertTrue(text.contains("Другие каналы: beta v1.2.0-beta.2"), text)
        val actions = lines.flatMap(::clicks)
        assertTrue(actions.contains("/pn updates set pnlibrary channel beta"))
        assertFalse(actions.contains("/pn updates set pnlibrary channel stable"))
        assertFalse(UpdateCatalogView.info(snapshot, bukkit.copy(editable = false)).flatMap(::clicks).any { it.contains(" set ") })
    }

    private fun catalog(snapshots: List<UpdateSnapshot>, page: Int = 1, updatesOnly: Boolean = false, routes: UpdateCatalogRoutes) =
        UpdateCatalogView.catalog(snapshots, page, updatesOnly, routes, now, ZoneOffset.UTC)

    private fun history(
        entries: List<UpdateHistoryEntry>,
        page: Int = 1,
        routes: UpdateCatalogRoutes = bukkit,
        totalPages: Int = 1,
        product: String? = null,
    ) = UpdateCatalogView.history(entries, page, routes, totalPages = totalPages, product = product, now = now, zone = ZoneOffset.UTC)

    private fun event(at: String, state: UpdateState, operation: String, actor: String, product: String, from: String, to: String) =
        UpdateHistoryEntry(UUID.randomUUID(), Instant.parse(at), UUID.randomUUID(), state,
            UpdateActor(UpdateActor.Kind.CONSOLE, actor.lowercase(), actor), operation,
            listOf(ProductChange(ProductId.of(product), SemanticVersion.parse(from), SemanticVersion.parse(to))), null)

    private fun snapshot(
        product: String,
        state: UpdateState,
        changelog: String? = null,
        channel: UpdateChannel = UpdateChannel.STABLE,
        latest: String = "1.1.0",
    ) = UpdateSnapshot(product, "1.0.0", latest, channel, state, 17, 8, false, null, null, changelog)

    private fun plain(component: Component) = PlainTextComponentSerializer.plainText().serialize(component)
    private fun clicks(component: Component): List<String> = listOfNotNull(component.clickEvent()?.value()) + component.children().flatMap(::clicks)
    private fun hover(component: Component): String =
        (component.hoverEvent()?.value() as? Component)?.let(::plain).orEmpty().also {
            assertTrue(component.hoverEvent()?.action() == HoverEvent.Action.SHOW_TEXT)
        }
}
