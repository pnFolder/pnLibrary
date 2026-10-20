package org.pnfolder.pnlibrary.core.updates.catalog

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
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
import org.pnfolder.pnlibrary.api.updates.ProductChange
import org.pnfolder.pnlibrary.api.updates.ProductId
import org.pnfolder.pnlibrary.api.updates.ReleaseSummary
import org.pnfolder.pnlibrary.api.updates.UpdateActor
import org.pnfolder.pnlibrary.api.updates.UpdateBackup
import org.pnfolder.pnlibrary.api.updates.UpdateBackupComponent
import org.pnfolder.pnlibrary.api.updates.UpdateChannel
import org.pnfolder.pnlibrary.api.updates.UpdateHistoryEntry
import org.pnfolder.pnlibrary.api.updates.UpdateSnapshot
import org.pnfolder.pnlibrary.api.updates.UpdateState
import org.pnfolder.pnlibrary.api.version.SemanticVersion
import org.pnfolder.pnlibrary.api.chat.ChatNotice
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.api.chat.ChatTone
import org.pnfolder.pnlibrary.core.updates.install.ComponentLedger
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class UpdateCatalogViewTest {
    private val bukkit = UpdateCatalogRoutes("/pn updates", update = { "/pn update $it" }, check = "/pn check")
    private val proxy = UpdateCatalogRoutes("/pnupdate")
    private val now = Instant.parse("2026-10-17T12:00:00Z")

    // --- catalog -------------------------------------------------------------------------------------------------

    @Test
    fun `catalog title counts plugins and updates and offers an update button per available plugin`() {
        val lines = catalog(
            listOf(
                snapshot("pnlibrary", UpdateState.UPDATE_AVAILABLE, "https://github.com/example/repo/blob/main/notes.md"),
                snapshot("currency", UpdateState.UP_TO_DATE),
            ),
            routes = bukkit,
        )
        val text = text(lines)
        assertEquals("Каталог  2 плагина · 1 к обновлению", plain(lines[1]))
        assertEquals(" Нажмите на плагин, чтобы открыть его карточку.", plain(lines[2]))
        assertTrue(text.contains("\n  currency v1.0.0\n"), text)
        assertTrue(text.contains("\n  pnlibrary v1.0.0 → v1.1.0 [Обновить]\n"), text)
        val actions = lines.flatMap(::clicks)
        assertTrue(actions.containsAll(listOf("/pn update pnlibrary", "/pn updates info pnlibrary", "https://github.com/example/repo/blob/main/notes.md")))
        assertTrue(actions.containsAll(listOf("/pn check", "/pn updates available", "/pn updates history", "/pn updates backups")))
        assertFalse(plain(row(lines, "currency")).contains("[Обновить]"))
    }

    @Test
    fun `a catalog row opens the plugin card and explains itself in a multi-line hover`() {
        val releases = listOf(ReleaseSummary("1.1.0", UpdateChannel.STABLE, Instant.parse("2026-10-01T00:00:00Z")))
        val snapshot = UpdateSnapshot("pnlibrary", "1.0.0", "1.1.0", UpdateChannel.STABLE, UpdateState.UPDATE_AVAILABLE,
            17, 8, true, null, "Каталог ответил", availableReleases = releases)
        val row = row(catalog(listOf(snapshot), routes = bukkit), "pnlibrary")
        assertEquals("/pn updates info pnlibrary", row.clickEvent()?.value())
        val card = hover(row)
        assertTrue(card.startsWith("pnlibrary\n"), card)
        assertTrue(card.contains("Статус: доступно обновление"))
        assertTrue(card.contains("Версия: v1.0.0 → v1.1.0"))
        assertTrue(card.contains("Выпущена: 1 октября · 16 д назад"))
        assertTrue(card.contains("Автозагрузка: включена"))
        assertTrue(card.contains("Каталог ответил"))
        assertTrue(card.endsWith("\n\nНажмите, чтобы открыть карточку"), card)
    }

    @Test
    fun `the title reports prepared updates and failures and reads as all clear otherwise`() {
        val busy = catalog(listOf(
            snapshot("a", UpdateState.UPDATE_STAGED), snapshot("b", UpdateState.FAILED), snapshot("c", UpdateState.INCOMPATIBLE),
        ), routes = proxy)
        assertEquals("Каталог  3 плагина · 1 к перезапуску · 2 ошибки", plain(busy[1]))
        assertEquals("  a v1.0.0 → v1.1.0  ждёт перезапуска", plain(row(busy, "a")))

        assertEquals("Каталог  1 плагин · всё актуально", plain(catalog(listOf(snapshot("a", UpdateState.UP_TO_DATE)), routes = proxy)[1]))
        val none = catalog(listOf(snapshot("a", UpdateState.UP_TO_DATE)), updatesOnly = true, routes = proxy)
        assertEquals("Обновления  1 плагин · всё актуально", plain(none[1]))
        assertTrue(none.any { plain(it) == "  Все плагины актуальны." })
    }

    @Test
    fun `rows keep a white name, gray versions and one colored word`() {
        val lines = catalog(listOf(snapshot("shop", UpdateState.FROZEN, channel = UpdateChannel.BETA)), routes = bukkit)
        val row = row(lines, "shop")
        assertEquals("  shop v1.0.0  beta  пауза", plain(row))
        val legacy = LegacyComponentSerializer.legacyAmpersand().serialize(row)
        assertTrue(legacy.contains("&fshop"), legacy)
        assertTrue(legacy.contains("&7v1.0.0"), legacy)
        assertTrue(legacy.contains("&eпауза"), legacy)
        assertFalse(plain(row(catalog(listOf(snapshot("plain", UpdateState.UP_TO_DATE)), routes = bukkit), "plain")).contains("stable"))
    }

    @Test
    fun `without an update route the available version is shown as plain text`() {
        val lines = catalog(listOf(snapshot("pnlibrary", UpdateState.UPDATE_AVAILABLE)), routes = proxy)
        assertEquals("  pnlibrary v1.0.0 → v1.1.0", plain(row(lines, "pnlibrary")))
        assertFalse(lines.flatMap(::clicks).any { it.startsWith("/pn update ") })
    }

    @Test
    fun `catalog paginates and the available filter excludes current components`() {
        val snapshots = (1..12).map { snapshot("module-$it", UpdateState.UPDATE_AVAILABLE) } + snapshot("current", UpdateState.UP_TO_DATE)
        val first = catalog(snapshots, 1, true, proxy)
        val second = catalog(snapshots, 2, true, proxy)
        assertEquals(10, first.count { plain(it).startsWith("  module-") })
        assertEquals(2, second.count { plain(it).startsWith("  module-") })
        assertFalse(first.drop(2).any { plain(it).contains("current") })
        assertTrue(text(first).contains("страница 1 из 2 [Следующая]"))
        assertTrue(text(second).contains("страница 2 из 2 [Предыдущая]"))
        assertTrue(first.flatMap(::clicks).contains("/pnupdate available 2"))
        assertTrue(second.flatMap(::clicks).contains("/pnupdate available 1"))
        assertTrue(text(catalog(snapshots, Int.MAX_VALUE, false, proxy)).contains("На этой странице пусто"))
    }

    @Test
    fun `only valid HTTPS changelog URLs become clickable`() {
        val safe = catalog(listOf(snapshot("safe", UpdateState.UPDATE_AVAILABLE, "https://github.com/example/repo/blob/main/notes.md")), routes = proxy)
        assertTrue(safe.flatMap(::clicks).any { it.startsWith("https://github.com/") })
        for (url in listOf("javascript:alert(1)", "file:///secret", "https://user:password@example.com/notes")) {
            val unsafe = catalog(listOf(snapshot("unsafe", UpdateState.UPDATE_AVAILABLE, url)), routes = proxy)
            assertFalse(unsafe.flatMap(::clicks).contains(url))
            assertFalse(text(unsafe).contains(url))
        }
    }

    // --- history -------------------------------------------------------------------------------------------------

    @Test
    fun `history groups events by day under accent headings and keeps time and actor in the hover`() {
        val today = event("2026-10-17T09:05:00Z", UpdateState.UPDATE_STAGED, "STAGE", "Admin", "pnlibrary", "1.0.0", "1.1.0")
        val yesterday = event("2026-10-16T18:00:00Z", UpdateState.UP_TO_DATE, "HEALTH", "System", "currency", "2.0.0", "2.1.0")
        val older = event("2026-09-12T10:00:00Z", UpdateState.FAILED, "STAGE", "Admin", "pnlibrary", "0.9.0", "1.0.0")
        val lines = history(listOf(today, yesterday, older))
        val text = text(lines)

        assertEquals("История", plain(lines[1]))
        assertTrue(text.indexOf("  «Сегодня»") < text.indexOf("  «Вчера»"))
        assertTrue(text.indexOf("  «Вчера»") < text.indexOf("  «12 сентября»"))
        assertTrue(text.contains("\n\n  «Вчера»\n"), text)
        assertTrue(text.contains("    pnlibrary  v1.0.0 → v1.1.0  подготовлено"), text)
        assertTrue(text.contains("    currency  v2.0.0 → v2.1.0  применено"))
        assertTrue(text.contains("    pnlibrary  v0.9.0 → v1.0.0  ошибка"))
        assertFalse(text.contains("Admin"))
        assertFalse(text.contains("страница 1 из 1"))
        val card = hover(row(lines, "v1.0.0 → v1.1.0"))
        assertTrue(card.contains("Итог: подготовлено, применится после перезапуска"))
        assertTrue(card.contains("Кто: Admin"))
    }

    @Test
    fun `history lines offer a per plugin filter and the filtered view does not`() {
        val entry = event("2026-10-17T09:05:00Z", UpdateState.UPDATE_STAGED, "STAGE", "Admin", "pnlibrary", "1.0.0", "1.1.0")
        val all = history(listOf(entry))
        assertTrue(all.flatMap(::clicks).contains("/pn updates history plugin pnlibrary"))
        assertTrue(hover(row(all, "pnlibrary  v1.0.0")).contains("Показать только pnlibrary\n/pn updates history plugin pnlibrary"))

        val filtered = history(listOf(entry), product = "pnlibrary")
        assertEquals("История  pnlibrary", plain(filtered[1]))
        assertNull(row(filtered, "v1.0.0 → v1.1.0").clickEvent())
        assertTrue(filtered.flatMap(::clicks).containsAll(listOf("/pn updates history", "/pn updates info pnlibrary")))
    }

    @Test
    fun `history navigation reports page numbers and links to older and newer pages`() {
        val entry = event("2026-10-17T09:05:00Z", UpdateState.UPDATE_STAGED, "STAGE", "Admin", "pnlibrary", "1.0.0", "1.1.0")
        val middle = history(listOf(entry), page = 2, totalPages = 4)
        assertTrue(text(middle).contains("  страница 2 из 4 [Новее] [Старее]"))
        assertTrue(middle.flatMap(::clicks).containsAll(listOf("/pn updates history 1", "/pn updates history 3", "/pn updates list")))

        assertFalse(history(listOf(entry), page = 4, totalPages = 4).flatMap(::clicks).contains("/pn updates history 5"))
        assertTrue(history(listOf(entry), totalPages = 2, product = "pnlibrary").flatMap(::clicks).contains("/pn updates history plugin pnlibrary 2"))
        assertTrue(history(emptyList()).any { plain(it) == " Событий пока нет." })
    }

    @Test
    fun `verified rollback is distinguished from a merely prepared rollback`() {
        val verified = event("2026-10-17T09:05:00Z", UpdateState.ROLLED_BACK, "HEALTH_ROLLBACK", "System", "pnlibrary", "2.0.0", "1.0.0")
        val prepared = event("2026-10-17T08:05:00Z", UpdateState.ROLLED_BACK, "ROLLBACK", "Admin", "pnlibrary", "2.0.0", "1.0.0")
        val text = text(history(listOf(verified, prepared)))
        assertTrue(text.contains("pnlibrary  откат к v2.0.0  подтверждён"), text)
        assertTrue(text.contains("pnlibrary  откат к v2.0.0  ждёт перезапуска"))
    }

    @Test
    fun `an event without version changes shows its message as plain text`() {
        val entry = UpdateHistoryEntry(UUID.randomUUID(), Instant.parse("2026-10-17T09:05:00Z"), UUID.randomUUID(),
            UpdateState.FAILED, UpdateActor.SYSTEM, "CHECK", emptyList(), "<red>HTTP 404</red>")
        val line = row(history(listOf(entry)), "HTTP 404")
        assertEquals("    <red>HTTP 404</red>  ошибка", plain(line))
    }

    @Test
    fun `component events say what was installed, removed, changed by hand or reconfigured`() {
        fun component(operation: String, from: String?, to: String, message: String? = null) = UpdateHistoryEntry(
            UUID.randomUUID(), Instant.parse("2026-10-17T09:05:00Z"), UUID.randomUUID(), UpdateState.UP_TO_DATE,
            UpdateActor.SYSTEM, operation, listOf(ProductChange(ProductId.of("shop"), from?.let(SemanticVersion::parse), SemanticVersion.parse(to))),
            message,
        )
        val text = text(history(listOf(
            component(ComponentLedger.OPERATION_ADDED, null, "1.0.0"),
            component(ComponentLedger.OPERATION_REMOVED, "1.0.0", "1.0.0"),
            component(ComponentLedger.OPERATION_VERSION_CHANGED, "1.0.0", "1.1.0"),
            component(ComponentLedger.OPERATION_SETTINGS_CHANGED, "1.0.0", "1.0.0", "канал: stable → beta"),
        )))
        assertTrue(text.contains("shop  v1.0.0  установлен"), text)
        assertTrue(text.contains("shop  v1.0.0  удалён"))
        assertTrue(text.contains("shop  v1.0.0 → v1.1.0  изменена вручную"))
        assertTrue(text.contains("shop  v1.0.0  канал: stable → beta"))
    }

    // --- settings and info ---------------------------------------------------------------------------------------

    @Test
    fun `settings draw every choice as a button and mark the current one`() {
        val lines = UpdateCatalogView.settings(snapshot("pnlibrary", UpdateState.UP_TO_DATE, channel = UpdateChannel.BETA), bukkit)
        val text = text(lines)
        assertEquals("pnlibrary  настройки", plain(lines[1]))
        assertEquals("  v1.0.0 · beta", plain(lines[2]))
        assertTrue(text.contains(" Нажмите на кнопку, чтобы выбрать. ✔ — выбрано сейчас."))
        val channel = row(lines, "Канал:")
        assertEquals("  Канал: [stable] [rc] [✔ beta] [alpha] [dev]", plain(channel))
        assertTrue(LegacyComponentSerializer.legacyAmpersand().serialize(channel).contains("&e[✔ beta]"), LegacyComponentSerializer.legacyAmpersand().serialize(channel))
        assertTrue(LegacyComponentSerializer.legacyAmpersand().serialize(channel).contains("[&cdev&7]"))
        assertEquals("  Автозагрузка: [вкл] [✔ выкл]", plain(row(lines, "Автозагрузка:")))
        assertEquals("  Пауза: [✔ нет] [1 ч] [6 ч] [1 д] [3 д] [7 д]", plain(row(lines, "Пауза:")))
        assertTrue(text.contains("Пауза — не дольше 7 дней."))
        assertTrue(LegacyComponentSerializer.legacyAmpersand().serialize(channel).startsWith("&f  Канал: "))

        val actions = lines.flatMap(::clicks)
        assertTrue(actions.containsAll(listOf(
            "/pn updates set pnlibrary channel stable", "/pn updates set pnlibrary channel alpha",
            "/pn updates set pnlibrary auto on", "/pn updates set pnlibrary hold 3d", "/pn updates set pnlibrary hold 7d",
        )))
        assertFalse(actions.contains("/pn updates set pnlibrary channel beta"))
        assertFalse(actions.contains("/pn updates set pnlibrary auto off"))
        assertFalse(actions.contains("/pn updates set pnlibrary hold off"))
    }

    @Test
    fun `a reader without edit rights sees the choices but cannot click them`() {
        val lines = UpdateCatalogView.settings(snapshot("pnlibrary", UpdateState.FROZEN), bukkit.copy(editable = false),
            heldUntil = now.plusSeconds(3600), now = now, zone = ZoneOffset.UTC)
        val text = text(lines)
        assertFalse(lines.flatMap(::runs).any { it.contains(" set ") })
        assertTrue(text.contains("Только просмотр"))
        assertFalse(text.contains("Нажмите на кнопку"))
        assertTrue(text.contains("Канал: [✔ stable] rc beta alpha dev"), text)
        assertTrue(text.contains("Пауза: до 17 окт. 13:00 · ещё 1 ч") || text.contains("Пауза: до 17 окт 13:00 · ещё 1 ч"), text)
        assertFalse(text.contains("[Снять]"))
    }

    @Test
    fun `a paused component shows when the pause ends and offers to lift or replace it`() {
        val until = now.plusSeconds(3L * 86_400)
        val lines = UpdateCatalogView.settings(snapshot("pnlibrary", UpdateState.FROZEN), bukkit, heldUntil = until, now = now, zone = ZoneOffset.UTC)
        val text = text(lines)
        assertTrue(text.contains("Пауза: [✔ до 20 окт. 12:00 · ещё 3 д] [Снять]") || text.contains("Пауза: [✔ до 20 окт 12:00 · ещё 3 д] [Снять]"), text)
        assertTrue(text.contains("Продлить: [1 ч] [6 ч] [1 д] [3 д] [7 д]"))
        assertFalse(lines.flatMap(::clicks).contains("/pn updates set pnlibrary hold 30d"))
        assertTrue(lines.flatMap(::clicks).containsAll(listOf("/pn updates set pnlibrary hold off", "/pn updates set pnlibrary hold 7d")))

        val info = text(UpdateCatalogView.info(snapshot("pnlibrary", UpdateState.FROZEN), bukkit, heldUntil = until, now = now, zone = ZoneOffset.UTC))
        assertTrue(info.contains("pnlibrary  приостановлено"))
        assertTrue(info.contains("· ещё 3 д"))
    }

    @Test
    fun `info card lists the details and offers an update only when one is available`() {
        val available = UpdateCatalogView.info(
            snapshot("pnlibrary", UpdateState.UPDATE_AVAILABLE, "https://github.com/example/repo/blob/main/notes.md"), bukkit,
        )
        val text = text(available)
        assertEquals("pnlibrary  доступно обновление", plain(available[1]))
        assertTrue(text.contains("  Версия: v1.0.0 → v1.1.0 [Изменения]"))
        assertTrue(text.contains("  Канал: stable [изменить]"))
        assertTrue(text.contains("  Автозагрузка: выключена [изменить]"))
        assertTrue(text.contains("  Требует: Java 8+"))
        assertTrue(text.contains("  [Обновить] [Настройки] [История] [Назад]"))
        assertTrue(available.flatMap(::clicks).containsAll(listOf("/pn update pnlibrary", "/pn updates history plugin pnlibrary", "/pn updates list")))

        val current = UpdateCatalogView.info(snapshot("pnlibrary", UpdateState.UP_TO_DATE, latest = "1.0.0"), bukkit)
        assertFalse(current.flatMap(::clicks).contains("/pn update pnlibrary"))
        assertTrue(text(current).contains("Версия: v1.0.0\n"))

        val oldJava = UpdateSnapshot("pnlibrary", "1.0.0", null, UpdateChannel.STABLE, UpdateState.INCOMPATIBLE, 8, 17, false, null, null)
        assertTrue(text(UpdateCatalogView.info(oldJava, bukkit)).contains("Требует: Java 17+ (на сервере 8)"))
    }

    @Test
    fun `the info card links each setting to the settings card`() {
        val lines = UpdateCatalogView.info(snapshot("shop", UpdateState.UP_TO_DATE), bukkit)
        listOf("Канал:", "Автозагрузка:", "Пауза:").forEach { label ->
            val row = row(lines, label)
            assertTrue(plain(row).endsWith("[изменить]"), plain(row))
            assertTrue(clicks(row).contains("/pn updates settings shop"))
        }
        assertFalse(UpdateCatalogView.info(snapshot("shop", UpdateState.UP_TO_DATE), bukkit.copy(editable = false)).any { plain(it).contains("[изменить]") })
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
        val text = text(lines)
        assertTrue(text.contains("Выпущена: 1 октября · 16 д назад"), text)
        assertTrue(text.contains("Другие каналы: beta v1.2.0-beta.2"), text)
        val actions = lines.flatMap(::clicks)
        assertTrue(actions.contains("/pn updates set pnlibrary channel beta"))
        assertFalse(actions.contains("/pn updates set pnlibrary channel stable"))
        assertFalse(UpdateCatalogView.info(snapshot, bukkit.copy(editable = false)).flatMap(::clicks).any { it.contains(" set ") })
    }

    // --- backups -------------------------------------------------------------------------------------------------

    @Test
    fun `backups show age and open separate confirmation flows rather than executing a restore`() {
        val id = UUID.randomUUID()
        val backup = UpdateBackup(id, now.minusSeconds(4L * 86_400), UUID.randomUUID(),
            listOf(UpdateBackupComponent(ProductId.of("pnlibrary"), "2.0.0", "1.0.0")), 2)
        val lines = UpdateCatalogView.backups(listOf(backup), 1, bukkit, now = now, zone = ZoneOffset.UTC)
        val actions = lines.flatMap(::clicks)
        assertEquals("Резервы  1 набор", plain(lines[1]))
        val row = row(lines, "pnlibrary")
        assertEquals("  pnlibrary v1.0.0  4 д назад [Откат] [+конфиги]", plain(row))
        assertTrue(hover(row).contains("Вернёт: pnlibrary v1.0.0 (сейчас v2.0.0)"))
        assertTrue(actions.contains("/pn updates rollback $id"))
        assertTrue(actions.contains("/pn updates rollback $id configs"))
        assertFalse(actions.any { it.endsWith(" confirm") })
        assertTrue(UpdateCatalogView.backups(emptyList(), 1, bukkit).any { plain(it).contains("Резервов пока нет") })
    }

    // --- readers -------------------------------------------------------------------------------------------------

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
        pages.forEach { page -> assertTrue(page.flatMap(::clicks).isEmpty(), text(page)) }
        val catalog = text(pages[0])
        assertTrue(catalog.contains("  «Команды»\n    /pn check — проверить обновления"), catalog)
        assertTrue(catalog.contains("pnlibrary v1.0.0 → v1.1.0\n      изменения: https://github.com/a/b"), catalog)
        assertFalse(catalog.contains("Нажмите"))
        assertTrue(text(pages[2]).contains("/pn updates set pnlibrary channel <stable|rc|beta|alpha|dev|default>"))
        assertTrue(text(pages[3]).contains("следующая: /pn updates history 2"))
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
        val text = text(all)
        assertTrue(text.contains("Каталог  2 плагина | 1 к обновлению"), text)
        assertTrue(text.contains("  pnlibrary v1.0.0 -> v1.1.0"), text)
        assertTrue(text.all { it.code < 128 || it in 'А'..'я' || it == 'ё' || it == 'Ё' }, text)
    }

    @Test
    fun `every page starts with an empty line and a bold accent title`() {
        val pages = listOf(
            catalog(listOf(snapshot("shop", UpdateState.UP_TO_DATE)), routes = bukkit),
            history(emptyList()),
            UpdateCatalogView.backups(emptyList(), 1, bukkit),
            UpdateCatalogView.info(snapshot("shop", UpdateState.UP_TO_DATE), bukkit),
            UpdateCatalogView.settings(snapshot("shop", UpdateState.UP_TO_DATE), bukkit),
            UpdateCatalogView.confirmation("Откат", listOf("Вернутся JAR."), "[Да]", "/pn x", "Выполнить", bukkit),
        )
        pages.forEach { page ->
            assertEquals("", plain(page[0]))
            val title = titleOf(page[1])
            assertTrue(title.hasDecoration(TextDecoration.BOLD), plain(page[1]))
            assertEquals(ChatPalette.ACCENT, title.color())
        }
        assertTrue(UpdateCatalogView.notice(ChatNotice(ChatTone.SUCCESS, "Готово"), bukkit).none { plain(it).isEmpty() })
    }

    @Test
    fun `only the title word is bold, never the counters beside it`() {
        val header = catalog(listOf(snapshot("shop", UpdateState.UP_TO_DATE)), routes = bukkit)[1]
        val legacy = LegacyComponentSerializer.legacyAmpersand().serialize(header)
        assertTrue(legacy.startsWith("&d&lКаталог&r"), legacy)
        assertEquals(1, Regex("&l").findAll(legacy).count(), legacy)
    }

    @Test
    fun `every palette role keeps its own color on servers without RGB`() {
        val expected = mapOf(
            ChatPalette.TEXT to NamedTextColor.WHITE, ChatPalette.SOFT to NamedTextColor.GRAY,
            ChatPalette.LABEL to NamedTextColor.LIGHT_PURPLE, ChatPalette.FAINT to NamedTextColor.DARK_GRAY,
            ChatPalette.ACCENT to NamedTextColor.LIGHT_PURPLE, ChatPalette.OK to NamedTextColor.GREEN,
            ChatPalette.INFO to NamedTextColor.AQUA, ChatPalette.WARN to NamedTextColor.YELLOW,
            ChatPalette.ORANGE to NamedTextColor.GOLD, ChatPalette.DANGER to NamedTextColor.RED,
        )
        expected.forEach { (color, legacy) -> assertEquals(legacy, NamedTextColor.nearestTo(color), color.asHexString()) }
    }

    @Test
    fun `buttons have gray brackets around a colored label`() {
        val lines = catalog(listOf(snapshot("shop", UpdateState.UP_TO_DATE)), routes = bukkit)
        val footer = LegacyComponentSerializer.legacyAmpersand().serialize(row(lines, "[Проверить]"))
        assertTrue(footer.contains("&7[&aПроверить&7]"), footer)
        assertTrue(footer.contains("&7[&dИстория&7]"), footer)
    }

    // --- notices and other pages ---------------------------------------------------------------------------------

    @Test
    fun `notices mark their tone and keep the detail on its own line`() {
        val lines = UpdateCatalogView.notice(ChatNotice(ChatTone.FAILURE, "Не удалось подготовить откат.", "HTTP 404"), bukkit)
        assertEquals(listOf("  ! Не удалось подготовить откат.", "    HTTP 404"), lines.map(::plain))
        assertEquals("  √ Готово", plain(UpdateCatalogView.notice(ChatNotice(ChatTone.SUCCESS, "Готово"), bukkit).single()))
        val console = UpdateCatalogView.notice(ChatNotice(ChatTone.PROGRESS, "Проверяю обновления…"), proxy.copy(interactive = false, ascii = true))
        assertEquals("  >> Проверяю обновления...", plain(console.single()))
    }

    @Test
    fun `a confirmation card offers one button, or the command to type when the reader cannot click`() {
        val card = UpdateCatalogView.confirmation("Перезапуск сервера", listOf("Игроков на сервере: 3."),
            "[Подтвердить перезапуск]", "/pn restart abc", "Выполнить", bukkit)
        assertEquals("Перезапуск сервера", plain(card[1]))
        assertEquals(listOf("/pn restart abc"), card.flatMap(::clicks))

        val text = UpdateCatalogView.confirmation("Перезапуск сервера", listOf("Игроков на сервере: 3."),
            "[Подтвердить перезапуск]", "/pn restart abc", "Выполнить", bukkit.copy(interactive = false))
        assertTrue(text.flatMap(::clicks).isEmpty())
        assertEquals("  Подтвердить: /pn restart abc", plain(text.last()))
    }

    @Test
    fun `the status card lists server facts and every component like the catalog`() {
        val lines = UpdateCatalogView.status(listOf("Ядро" to "Paper 1.8.8", "Java" to "OpenJDK 8"),
            listOf(snapshot("shop", UpdateState.UP_TO_DATE), snapshot("pnlibrary", UpdateState.UPDATE_AVAILABLE)),
            bukkit, "https://discord.gg/example", now, ZoneOffset.UTC)
        val text = text(lines)
        assertEquals("Состояние  2 плагина · 1 к обновлению", plain(lines[1]))
        assertTrue(text.contains("  Ядро: Paper 1.8.8"))
        assertTrue(text.indexOf("pnlibrary v1.0.0 → v1.1.0") < text.indexOf("shop v1.0.0"))
        assertTrue(lines.flatMap(::clicks).containsAll(listOf("/pn updates list", "/pn check", "https://discord.gg/example")))
    }

    @Test
    fun `the join notice lists only pending updates and is empty when everything is current`() {
        val current = listOf(snapshot("shop", UpdateState.UP_TO_DATE))
        assertTrue(UpdateCatalogView.announcement(current, bukkit, now = now, zone = ZoneOffset.UTC).isEmpty())

        val lines = UpdateCatalogView.announcement(current + snapshot("pnlibrary", UpdateState.UPDATE_AVAILABLE), bukkit,
            now = now, zone = ZoneOffset.UTC)
        val text = text(lines)
        assertEquals("Обновления pnFolder  1 плагин · 1 к обновлению", plain(lines[1]))
        assertTrue(text.contains("pnlibrary v1.0.0 → v1.1.0"))
        assertFalse(text.contains("shop"))
        assertTrue(lines.flatMap(::clicks).containsAll(listOf("/pn update pnlibrary", "/pn updates available")))
    }

    // --- helpers -------------------------------------------------------------------------------------------------

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

    /** The bold title of a header line, which is either the line itself or its first child. */
    private fun titleOf(header: Component): Component =
        if (header.hasDecoration(TextDecoration.BOLD)) header else header.children().first()

    private fun row(lines: List<Component>, containing: String): Component = lines.first { plain(it).contains(containing) }
    private fun text(lines: List<Component>): String = lines.joinToString("\n", transform = ::plain)
    private fun plain(component: Component) = PlainTextComponentSerializer.plainText().serialize(component)
    /** Commands a click runs right away, unlike suggestions that only fill the chat box. */
    private fun runs(component: Component): List<String> =
        listOfNotNull(component.clickEvent()?.takeIf { it.action() == ClickEvent.Action.RUN_COMMAND }?.value()) +
            component.children().flatMap(::runs)
    private fun clicks(component: Component): List<String> = listOfNotNull(component.clickEvent()?.value()) + component.children().flatMap(::clicks)
    private fun hover(component: Component): String =
        (component.hoverEvent()?.value() as? Component)?.let(::plain).orEmpty().also {
            assertTrue(component.hoverEvent()?.action() == HoverEvent.Action.SHOW_TEXT)
        }
}
