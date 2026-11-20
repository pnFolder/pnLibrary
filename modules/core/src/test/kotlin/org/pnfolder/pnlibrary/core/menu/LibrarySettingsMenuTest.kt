package org.pnfolder.pnlibrary.core.menu

import net.kyori.adventure.text.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.updates.UpdateActor
import org.pnfolder.pnlibrary.core.menu.data.MenuData
import org.pnfolder.pnlibrary.core.menu.data.SettingOutcome
import org.pnfolder.pnlibrary.core.menu.data.SettingRow
import org.pnfolder.pnlibrary.core.menu.style.DATE_FILTER
import org.pnfolder.pnlibrary.core.menu.style.FILTER
import org.pnfolder.pnlibrary.core.menu.style.NEXT
import org.pnfolder.pnlibrary.core.menu.style.PAGE_SIZE
import org.pnfolder.pnlibrary.core.settings.ChoiceSource
import org.pnfolder.pnlibrary.core.settings.LibrarySettings
import org.pnfolder.pnlibrary.core.settings.SettingApply
import org.pnfolder.pnlibrary.core.settings.SettingsRegistry
import java.lang.reflect.Proxy
import java.util.UUID

class LibrarySettingsMenuTest {
    private class Viewer : MenuViewer {
        val opened = mutableListOf<Menu>()
        override val id: UUID = UUID.randomUUID()
        override val name = "Admin"
        override val online = true
        override val remote = false
        override val actor = UpdateActor(UpdateActor.Kind.PLAYER, id.toString(), name)
        override fun hasPermission(permission: String) = true
        override fun open(menu: Menu) { opened += menu }
        override fun close() {}
        val sent = mutableListOf<Component>()
        override fun send(lines: List<Component>) { sent += lines }
        override fun runCommand(command: String) {}
    }

    private object Host : MenuHost {
        override fun sync(action: () -> Unit) = action()
        override fun later(delayMillis: Long, action: () -> Unit) = action()
        override fun async(action: () -> Unit) = action()
        override fun openRemote(viewer: MenuViewer, target: String) = false
        override fun owner(owner: Any?): OwnerInfo? = null
        override fun ownerNamed(name: String): OwnerInfo? = null
        override fun ownerOf(type: Class<*>?): OwnerInfo? = null
    }

    private val changes = mutableListOf<Pair<String, String?>>()

    private fun rows(current: Map<String, String> = emptyMap()): List<SettingRow> = SettingsRegistry.all.map { spec ->
        val value = current[spec.key] ?: spec.valueIn(LibrarySettings())
        SettingRow(
            spec.key, spec.title, spec.kind.name, spec.description, value, spec.default, value != spec.default, spec.range(), spec.examples,
            spec.applies == SettingApply.LIVE, spec.choices?.id.orEmpty(),
        )
    }

    private fun data(settings: List<SettingRow>): MenuData =
        Proxy.newProxyInstance(MenuData::class.java.classLoader, arrayOf(MenuData::class.java)) { _, method, args ->
            when {
                method.name == "settings" -> settings
                method.name == "changeSetting" -> {
                    changes += args[0] as String to args[1] as String?
                    SettingOutcome(true, "ok")
                }
                method.returnType == List::class.java -> emptyList<Any>()
                method.returnType == String::class.java -> "1.0"
                method.returnType == Int::class.javaPrimitiveType -> 0
                method.returnType == Boolean::class.javaPrimitiveType -> false
                else -> null
            }
        } as MenuData

    private fun plain(text: String) = text.replace(Regex("§."), "")

    private fun click(viewer: Viewer, menu: Menu, slot: Int, how: ClickType = ClickType.LEFT) {
        requireNotNull(menu.items.getValue(slot).handler).handle(MenuClick(viewer, slot, how))
    }

    private fun slotNamed(menu: Menu, name: String): Int =
        menu.items.entries.first { (_, item) -> item.item?.name?.let(::plain) == name }.key

    private fun loreOf(menu: Menu, name: String): List<String> = requireNotNull(menu.items.getValue(slotNamed(menu, name)).item).lore

    private fun settingsScreen(viewer: Viewer, settings: List<SettingRow>): Menu {
        LibraryMenu(data(settings), Host).home(viewer)
        click(viewer, viewer.opened.last(), 20)
        click(viewer, viewer.opened.last(), 22)
        return viewer.opened.last()
    }

    @Test
    fun `a switch shows Russian words and marks the default option in grey italics`() {
        val lore = loreOf(settingsScreen(Viewer(), rows()), "Приватность отчёта")
        val text = lore.joinToString("\n") { plain(it) }

        assertTrue("включено" in text && "выключено (по умолчанию)" in text, text)
        assertFalse("false" in text || "true" in text, text)
        assertFalse("По умолчанию" in text, "the mark replaces the separate default line")
        assertEquals(1, lore.count { "(по умолчанию)" in it })
        val marked = lore.single { "(по умолчанию)" in it }
        assertTrue(marked.endsWith("§o(по умолчанию)"), "the note is italic and comes last")
        assertTrue(marked.indexOf("§o") > marked.indexOf("выключено"))
    }

    @Test
    fun `the default mark follows the default, not the current value`() {
        val text = loreOf(settingsScreen(Viewer(), rows(mapOf("downloads.enabled" to "false"))), "Загрузки").joinToString("\n") { plain(it) }

        assertTrue("включено (по умолчанию)" in text, text)
        assertTrue("➤ выключено" in text, text)
    }

    @Test
    fun `a number shows its default only when it was changed`() {
        val unchanged = loreOf(settingsScreen(Viewer(), rows()), "Хранение истории").joinToString("\n") { plain(it) }
        val after = loreOf(settingsScreen(Viewer(), rows(mapOf("history-retention-days" to "90"))), "Хранение истории").joinToString("\n") { plain(it) }

        assertFalse("По умолчанию" in unchanged, unchanged)
        assertTrue("По умолчанию" in after && "90" in after, after)
    }

    private fun zoneList(viewer: Viewer, current: String = "Europe/Berlin"): Menu {
        val screen = settingsScreen(viewer, rows(mapOf("time-zone" to current)))
        click(viewer, screen, slotNamed(screen, "Часовой пояс"), ClickType.SHIFT_LEFT)
        return viewer.opened.last()
    }

    private fun titleOf(menu: Menu) = plain(menu.title)

    private fun nameAt(menu: Menu, slot: Int) = menu.items.getValue(slot).item?.name?.let(::plain)

    @Test
    fun `a time zone opens one list with the time and offset of every zone, the current one green and not clickable`() {
        val viewer = Viewer()
        val list = zoneList(viewer)
        val zones = ChoiceSource.ZONES

        assertTrue(titleOf(list).contains("Часовой пояс "), titleOf(list))
        assertTrue(titleOf(list).endsWith("1/${(zones.all.size - 1) / PAGE_SIZE + 1}"), titleOf(list))
        val first = nameAt(list, 0)
        assertEquals(zones.view(0, false, "").first(), first, "the default order is by offset")
        val lore = loreOf(list, first.orEmpty()).joinToString("\n") { plain(it) }
        assertTrue(Regex("""\[ Сейчас там \] \d\d:\d\d""").containsMatchIn(lore), lore)
        assertTrue(Regex("""\[ Смещение \] UTC([+-]\d\d:\d\d)?""").containsMatchIn(lore), lore)
        val shown = zones.view(0, false, "")
        val berlinPage = shown.indexOf("Europe/Berlin") / PAGE_SIZE
        if (berlinPage == 0) assertNull(list.items.getValue(slotNamed(list, "Europe/Berlin")).handler)
        assertEquals("Ввести в чате", nameAt(list, DATE_FILTER))
        assertEquals("Сортировка", nameAt(list, FILTER))
        assertEquals("Популярные", nameAt(list, 51))
        assertEquals("Поиск", nameAt(list, 52))
    }

    @Test
    fun `the sort button cycles the order with a tooltip of options and the order survives paging`() {
        val viewer = Viewer()
        val list = zoneList(viewer)
        val zones = ChoiceSource.ZONES
        val tooltip = loreOf(list, "Сортировка").joinToString("\n") { plain(it) }

        assertTrue("➤ по смещению UTC" in tooltip && "по алфавиту" in tooltip, tooltip)
        click(viewer, list, FILTER)
        val alphabetical = viewer.opened.last()
        assertEquals(zones.all.first(), nameAt(alphabetical, 0))
        assertTrue("➤ по алфавиту" in loreOf(alphabetical, "Сортировка").joinToString("\n") { plain(it) })

        click(viewer, alphabetical, NEXT)
        assertEquals(zones.all[PAGE_SIZE], nameAt(viewer.opened.last(), 0), "the next page keeps the alphabet")
        assertTrue(titleOf(viewer.opened.last()).endsWith("2/${(zones.all.size - 1) / PAGE_SIZE + 1}"))

        click(viewer, alphabetical, FILTER)
        assertEquals(zones.view(0, false, "").first(), nameAt(viewer.opened.last(), 0), "it cycles back")
    }

    @Test
    fun `the popular button shows the curated zones in their order and hides the sort`() {
        val viewer = Viewer()
        val list = zoneList(viewer)
        assertTrue("➤ Все" in loreOf(list, "Популярные").joinToString("\n") { plain(it) })

        click(viewer, list, 51)
        val popular = viewer.opened.last()

        assertTrue(titleOf(popular).contains("популярные"), titleOf(popular))
        ChoiceSource.ZONES.popular.forEachIndexed { index, zone -> assertEquals(zone, nameAt(popular, index)) }
        assertNull(popular.items[FILTER]?.handler)
        assertTrue("➤ Популярные" in loreOf(popular, "Популярные").joinToString("\n") { plain(it) })

        click(viewer, popular, 51)
        assertEquals(ChoiceSource.ZONES.view(0, false, "").first(), nameAt(viewer.opened.last(), 0))
    }

    @Test
    fun `a click on a zone writes the setting`() {
        val viewer = Viewer()
        val list = zoneList(viewer)
        click(viewer, list, 51)
        val popular = viewer.opened.last()

        click(viewer, popular, slotNamed(popular, "Europe/Moscow"))

        assertEquals(listOf("time-zone" to "Europe/Moscow"), changes)
        assertTrue(titleOf(viewer.opened.last()).contains("Параметры библиотеки"), "it returns to the settings")
    }

    @Test
    fun `pick opens the list narrowed by a filter, the reset button and the wheel on search clear it`() {
        val viewer = Viewer()
        val menu = LibraryMenu(data(rows()), Host)

        menu.pickSetting(viewer, "time-zone", "new york")
        val found = viewer.opened.last()
        assertTrue(titleOf(found).contains("«new york»"), titleOf(found))
        assertEquals("America/New_York", nameAt(found, 0))
        assertNull(found.items[1]?.handler, "only one zone matches")
        assertEquals("Сбросить поиск", nameAt(found, 51))

        click(viewer, found, 51)
        assertFalse(titleOf(viewer.opened.last()).contains("«"))
        assertNotNull(viewer.opened.last().items.getValue(1).handler)

        menu.pickSetting(viewer, "time-zone", "new york")
        click(viewer, viewer.opened.last(), 52, ClickType.MIDDLE)
        assertFalse(titleOf(viewer.opened.last()).contains("«"))
    }

    @Test
    fun `a search with no match says nothing was found and a wrong key is answered`() {
        val viewer = Viewer()
        val menu = LibraryMenu(data(rows()), Host)

        menu.pickSetting(viewer, "time-zone", "qqqq")
        assertTrue(viewer.opened.last().items.values.any { it.item?.name?.let(::plain) == "Ничего не найдено" })

        val before = viewer.opened.size
        menu.pickSetting(viewer, "privacy", "")
        assertEquals(before, viewer.opened.size, "a setting without a list opens nothing")
    }

    @Test
    fun `the search button puts the command with the key into the chat`() {
        val viewer = Viewer()
        val list = zoneList(viewer)

        click(viewer, list, 52)

        val command = viewer.sent.mapNotNull { it.clickEvent()?.value() }
        assertEquals(listOf("/pn settings pick time-zone "), command)
    }

    private fun zoneTooltip(menu: Menu) = loreOf(menu, "Часовой пояс").map { plain(it) }

    private fun clickZone(viewer: Viewer, current: String, how: ClickType): Menu {
        val screen = settingsScreen(viewer, rows(mapOf("time-zone" to current)))
        click(viewer, screen, slotNamed(screen, "Часовой пояс"), how)
        return screen
    }

    @Test
    fun `a time zone shows the rolling window over every zone with counts, offset, local time and hints`() {
        val text = zoneTooltip(settingsScreen(Viewer(), rows(mapOf("time-zone" to "Europe/Berlin")))).joinToString("\n")
        val all = ChoiceSource.ZONES.all

        assertTrue("[ Значение ] Europe/Berlin" in text, text)
        assertTrue("[ Вариант ] ${all.indexOf("Europe/Berlin") + 1} из ${all.size}" in text, text)
        assertTrue(Regex("""\[ Смещение \] UTC([+-]\d\d:\d\d)?""").containsMatchIn(text), text)
        assertTrue(Regex("""\[ Сейчас там \] \d\d:\d\d""").containsMatchIn(text), text)
        assertTrue("➤ Europe/Berlin" in text, text)
        assertTrue("▲ выше ещё" in text && "▼ ниже ещё" in text, text)
        assertTrue("Клик  следующий" in text && "Правый клик  предыдущий" in text && "Shift + клик  весь список" in text, text)
        assertFalse("По умолчанию" in text.substringBefore("➤"), "no default line while the zone is unchanged")
    }

    @Test
    fun `the window scrolls with the value and shows the lines above`() {
        val text = zoneTooltip(settingsScreen(Viewer(), rows(mapOf("time-zone" to "Europe/Moscow")))).joinToString("\n")

        assertTrue("▲ выше ещё" in text && "▼ ниже ещё" in text, text)
        assertTrue("➤ Europe/Moscow" in text, text)
    }

    @Test
    fun `a click rolls to the next zone, a right click to the previous one, and both wrap`() {
        val all = ChoiceSource.ZONES.all
        clickZone(Viewer(), "Europe/Berlin", ClickType.LEFT)
        clickZone(Viewer(), "Europe/Berlin", ClickType.RIGHT)
        clickZone(Viewer(), all.last(), ClickType.LEFT)
        clickZone(Viewer(), all.first(), ClickType.RIGHT)

        val berlin = all.indexOf("Europe/Berlin")
        assertEquals(listOf("time-zone"), changes.map { it.first }.distinct())
        assertEquals(listOf(all[berlin + 1], all[berlin - 1], all.first(), all.last()), changes.map { it.second })
    }

    @Test
    fun `a click writes the value and shows the same page again`() {
        val viewer = Viewer()
        clickZone(viewer, "Europe/Berlin", ClickType.LEFT)

        assertTrue(titleOf(viewer.opened.last()).contains("Параметры библиотеки"))
    }

    @Test
    fun `an unknown zone is shown first, counted without a number, and a click goes to the first zone`() {
        val all = ChoiceSource.ZONES.all
        val lines = zoneTooltip(settingsScreen(Viewer(), rows(mapOf("time-zone" to "US/Pacific"))))
        val text = lines.joinToString("\n")

        assertTrue("[ Вариант ] — из ${all.size}" in text, text)
        assertTrue(lines.any { "US/Pacific" in it && "➤" in it }, text)

        clickZone(Viewer(), "US/Pacific", ClickType.LEFT)
        clickZone(Viewer(), "US/Pacific", ClickType.RIGHT)
        assertEquals(listOf(all.first(), all.last()), changes.map { it.second })
    }

    @Test
    fun `Shift click opens the full list of all zones and writes nothing`() {
        val list = zoneList(Viewer(), "Europe/Samara")

        assertTrue(changes.isEmpty())
        assertTrue(titleOf(list).endsWith("1/${(ChoiceSource.ZONES.all.size - 1) / PAGE_SIZE + 1}"), titleOf(list))
    }

    @Test
    fun `the wheel resets a changed zone and shows the default only then`() {
        val viewer = Viewer()
        val screen = settingsScreen(viewer, rows(mapOf("time-zone" to "Asia/Tokyo")))
        val text = zoneTooltip(screen).joinToString("\n")
        assertTrue("[ По умолчанию ]" in text && "Колёсико  вернуть значение по умолчанию" in text, text)

        click(viewer, screen, slotNamed(screen, "Часовой пояс"), ClickType.MIDDLE)

        assertEquals(listOf("time-zone" to null), changes)
    }
}
