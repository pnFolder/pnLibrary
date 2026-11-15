package org.pnfolder.pnlibrary.core.menu.screens

import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity
import org.pnfolder.pnlibrary.api.updates.UpdateSnapshot
import org.pnfolder.pnlibrary.core.menu.MenuBuilder
import org.pnfolder.pnlibrary.core.menu.MenuRoutes
import org.pnfolder.pnlibrary.core.menu.MenuScreens
import org.pnfolder.pnlibrary.core.menu.MenuSession
import org.pnfolder.pnlibrary.core.menu.MenuViewer
import org.pnfolder.pnlibrary.core.menu.Menus
import org.pnfolder.pnlibrary.core.menu.style.HOLDS
import org.pnfolder.pnlibrary.core.menu.style.Icons
import org.pnfolder.pnlibrary.core.menu.style.ROUTES
import org.pnfolder.pnlibrary.core.menu.style.SETTINGS
import org.pnfolder.pnlibrary.core.menu.style.SMALL_BACK
import org.pnfolder.pnlibrary.core.menu.style.SMALL_ROWS
import org.pnfolder.pnlibrary.core.menu.style.Style
import org.pnfolder.pnlibrary.core.menu.style.WRAP_WIDTH
import org.pnfolder.pnlibrary.core.menu.style.backItem
import org.pnfolder.pnlibrary.core.menu.style.nanosText
import org.pnfolder.pnlibrary.core.menu.style.menuFrame
import org.pnfolder.pnlibrary.core.menu.style.icon
import org.pnfolder.pnlibrary.core.menu.style.timeLeft
import org.pnfolder.pnlibrary.core.menu.style.menuTitle
import org.pnfolder.pnlibrary.core.menu.style.wrapText
import org.pnfolder.pnlibrary.core.updates.catalog.UpdateCatalogView
import org.pnfolder.pnlibrary.updates.policy.UpdateDefaults
import java.time.Duration
import java.time.Instant

private const val SUPPORT_TOOL_SLOT = 16
private const val CYCLE_HOLDS = 3

/** Menu screens for library settings. */
internal class SettingsScreens(session: MenuSession, routes: MenuRoutes) : MenuScreens(session, routes) {
    private fun pauseFootnote(registered: Int, unregistered: Int): List<String> = buildList {
        add("")
        add(Style.description("Пауза касается плагинов с обновлениями: $registered."))
        if (unregistered > 0) add(Style.description("Без регистрации обновлений: $unregistered, их пауза не касается."))
    }

    fun settings(player: MenuViewer) {
        val all = snapshots()
        val builder = menuFrame(Menus.chest(menuTitle("Настройки")).rows(SMALL_ROWS + 1), SMALL_ROWS + 1)
        howItWorks(builder)
        autoForAll(builder, all)
        downloadsForMissing(builder)
        pauseForAll(builder, all)
        builder.button(SMALL_BACK + 9, backItem(), go { routes.home(it) })
        show(player, builder.build())
    }

    private fun howItWorks(builder: MenuBuilder) {
        builder.slot(
            4,
            icon(
                Icons.SETTINGS, "REDSTONE", "Как это работает",
                Style.lore(
                    listOf("Проверка и сообщения включены всегда, отключить библиотеку нельзя."),
                    listOf(
                        "Проверка" to "каждые ${UpdateDefaults.CHECK_INTERVAL.toHours()} ч",
                        "Консоль" to "сообщения всегда",
                        "Канал" to "только dev",
                    ),
                ),
            ),
        )
    }

    private fun autoForAll(builder: MenuBuilder, all: List<UpdateSnapshot>) {
        val automatic = all.count { it.automaticDownload }
        val everyoneAuto = all.isNotEmpty() && automatic == all.size
        val autoNote = if (!everyoneAuto && automatic > 0) listOf("Сейчас" to "у $automatic из ${all.size}") else emptyList()
        builder.button(
            11,
            icon(
                Icons.TOGGLE, "REDSTONE_TORCH", "Автозагрузка для всех",
                Style.lore(
                    listOf("Готовить новые версии всех плагинов сразу, без команды."), autoNote,
                    Style.left(if (everyoneAuto) "выключить у всех" else "включить у всех"),
                    extra = Style.choices(listOf("включена у всех", "выключена"), if (everyoneAuto) 0 else 1),
                ),
                alternative = "REDSTONE_TORCH_ON", good = everyoneAuto, muted = !everyoneAuto && automatic == 0,
            ),
            go { who ->
                allowed(who, SETTINGS) {
                    val done = if (everyoneAuto) "Автозагрузка выключена для всех плагинов." else "Автозагрузка включена для всех плагинов."
                    routes.everyone(who, done) { routes.setAutomatic(it, !everyoneAuto, who) }
                }
            },
        )
    }

    private fun downloadsForMissing(builder: MenuBuilder) {
        val automatic = data.downloadsAutomatic() ?: return
        builder.button(
            13,
            icon(
                Icons.TOGGLE, "HOPPER", "Недостающие плагины",
                Style.lore(
                    wrapText("Если плагину нужен другой плагин, скачать его самому. Иначе в консоль придут ссылки и код: /pn deps install <код>.", WRAP_WIDTH, 4),
                    emptyList(), Style.left(if (automatic) "выключить" else "включить"),
                    extra = Style.choices(listOf("скачивать самому", "только сообщать"), if (automatic) 0 else 1),
                ),
                alternative = "REDSTONE_TORCH_ON", good = automatic, muted = !automatic,
            ),
            go { who ->
                allowed(who, SETTINGS) {
                    data.setDownloadsAutomatic(!automatic)
                    settings(who)
                }
            },
        )
    }

    private fun pauseForAll(builder: MenuBuilder, all: List<UpdateSnapshot>) {
        val untils = all.mapNotNull { data.heldUntil(it.product) }.filter { it.isAfter(Instant.now()) }
        val longest = untils.maxOrNull()?.let { Duration.between(Instant.now(), it) }
        val pauseChoices = listOf("нет паузы") + HOLDS.take(CYCLE_HOLDS).map { "на ${it.first}" }
        val pauseSelected = longest?.let { span ->
            1 + (HOLDS.take(CYCLE_HOLDS).indexOfFirst { it.second >= span }.takeIf { it >= 0 } ?: (CYCLE_HOLDS - 1))
        } ?: 0
        val pauseNote = buildList {
            if (untils.isNotEmpty()) add("Осталось" to timeLeft(longest!!))
            if (untils.isNotEmpty()) add("На паузе" to "${untils.size} из ${all.size}")
        }
        builder.button(
            15,
            icon(
                Icons.CLOCK, "CLOCK", "Пауза для всех",
                Style.lore(
                    listOf(
                        "Все плагины на время не обновляются.",
                        "Срок заменяет личные паузы плагинов; «нет паузы» снимает и их.",
                    ),
                    pauseNote, Style.left("следующий срок"),
                    extra = Style.choices(pauseChoices, pauseSelected) + pauseFootnote(all.size, connectedCount() - all.size),
                ),
                alternative = "WATCH", warn = untils.isNotEmpty(),
            ),
            go { who -> allowed(who, SETTINGS) { cyclePause(who, (pauseSelected + 1) % pauseChoices.size) } },
        )
    }

    private fun cyclePause(who: MenuViewer, nextIndex: Int) {
        val duration = HOLDS.take(CYCLE_HOLDS).getOrNull(nextIndex - 1)
        val done = if (duration == null) "Все паузы сняты." else "Обновления всех плагинов приостановлены: ${duration.first}."
        routes.everyone(who, done) { routes.hold(it, duration?.second, who) }
    }

    fun tools(player: MenuViewer) {
        val builder = menuFrame(Menus.chest(menuTitle("Инструменты")).rows(SMALL_ROWS + 1), SMALL_ROWS + 1)
        val entries = listOf(
            Triple(Icons.CHECK, "COMPASS", "Статус сервера") to ("Версии сервера и всех плагинов." to "pn status"),
            Triple(Icons.CHANGELOG, "WRITTEN_BOOK", "Отчёт для поддержки") to ("Зашифрованный отчёт, который можно отправить нам." to "pn debug"),
            Triple(Icons.RELEASES, "GOLD_NUGGET", "Валюты") to ("Валюты и балансы игроков." to "pn money list"),
        )
        entries.forEachIndexed { index, (face, info) ->
            val (texture, material, label) = face
            val (description, command) = info
            builder.button(
                10 + index,
                icon(texture, material, label, Style.lore(listOf(description), listOf("Команда" to "/$command"), Style.left("выполнить"))),
                go { who -> who.close(); runCommand(who, command) },
            )
        }
        builder.button(
            SUPPORT_TOOL_SLOT,
            icon(Icons.SUPPORT, "PAPER", "Поддержка", Style.lore(listOf("Ссылка на помощь."), emptyList(), Style.left("получить"))),
            go { support(it) },
        )
        builder.button(SMALL_BACK + 9, backItem(), go { routes.home(it) })
        show(player, builder.build())
    }

    fun support(player: MenuViewer) {
        player.close()
        val card = UpdateCatalogView.support(PnLibraryIdentity.SUPPORT_URL, ROUTES)
        deliver(player, card)
    }
}
