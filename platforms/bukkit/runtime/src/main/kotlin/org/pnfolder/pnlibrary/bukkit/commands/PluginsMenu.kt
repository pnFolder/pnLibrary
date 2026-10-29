package org.pnfolder.pnlibrary.bukkit.commands

import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.pnfolder.pnlibrary.api.chat.ChatNotice
import org.pnfolder.pnlibrary.api.chat.ChatTone
import org.pnfolder.pnlibrary.api.events.DispatchMode
import org.pnfolder.pnlibrary.api.events.EventDescription
import org.pnfolder.pnlibrary.api.events.EventInfo
import org.pnfolder.pnlibrary.api.events.EventTrace
import org.pnfolder.pnlibrary.api.events.ListenerInfo
import org.pnfolder.pnlibrary.api.platform.platform
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity
import org.pnfolder.pnlibrary.api.tasks.TaskSnapshot
import org.pnfolder.pnlibrary.api.tasks.TaskStatus
import org.pnfolder.pnlibrary.api.updates.UpdateChannel
import org.pnfolder.pnlibrary.api.updates.UpdateSnapshot
import org.pnfolder.pnlibrary.api.updates.UpdateState
import org.pnfolder.pnlibrary.api.updates.backup.UpdateBackup
import org.pnfolder.pnlibrary.api.updates.backup.UpdateHistoryEntry
import org.pnfolder.pnlibrary.bukkit.api.BukkitPlatform
import org.pnfolder.pnlibrary.bukkit.inventory.Menu
import org.pnfolder.pnlibrary.bukkit.inventory.MenuBuilder
import org.pnfolder.pnlibrary.bukkit.inventory.MenuClickHandler
import org.pnfolder.pnlibrary.bukkit.inventory.Menus
import org.pnfolder.pnlibrary.common.time.DisplayZone
import org.pnfolder.pnlibrary.core.commands.TaskLabels
import org.pnfolder.pnlibrary.core.commands.TaskPeriod
import org.pnfolder.pnlibrary.core.commands.TaskState
import org.pnfolder.pnlibrary.core.lifecycle.JournalGroup
import org.pnfolder.pnlibrary.core.lifecycle.TaskControl
import org.pnfolder.pnlibrary.core.lifecycle.cancelTask
import org.pnfolder.pnlibrary.core.lifecycle.journalDetail
import org.pnfolder.pnlibrary.core.lifecycle.journalGroups
import org.pnfolder.pnlibrary.core.lifecycle.runTaskNow
import org.pnfolder.pnlibrary.core.lifecycle.taskSnapshots
import org.pnfolder.pnlibrary.core.updates.catalog.CatalogLabels
import org.pnfolder.pnlibrary.core.updates.catalog.UpdateCatalogView
import org.pnfolder.pnlibrary.core.updates.policy.UpdateDefaults
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.bukkit.command.CommandSender as NativeSender
import org.pnfolder.pnlibrary.api.commands.CommandSender as Reply

/**
 * The inventory menu of `/pn gui`: everything the update commands do, on several connected screens.
 *
 * Screens: the home page, the list of plugins (all, with updates, with problems), the card of one plugin, its
 * channel, pause and releases, the history of one plugin or of all and one entry of it, the saved backups with a
 * rollback choice, the journal with one event, the tasks of every plugin, and the plugin events (event bus) with their listeners, days and
 * recorded calls.
 * Every action calls the same services as the commands and asks for the same permissions, so the audit record is the
 * same, and every answer is a chat notice in the library's own style. Buttons are textured heads with an ordinary
 * item as the fallback, so the menu opens on every server from 1.8.
 */
internal class PluginsMenu(
    private val plugin: Plugin,
    private val library: PnLibrary,
    private val replies: BukkitCommandReplies,
) {
    private enum class Filter(val title: String) { ALL("Плагины"), UPDATES("Обновления"), PROBLEMS("Проблемы") }

    private enum class Tone(val from: String, val to: String) {
        NORMAL("D19BFF", "F5C6FF"),
        GOOD("6EE7A0", "B8F5D2"),
        WARN("FDE047", "FFB347"),
        DANGER("FF7A7A", "FFB0A0"),
        MUTED("9AA4AD", "C9D0D6"),
    }

    private enum class BusFilter(val label: String, val keep: (EventInfo) -> Boolean) {
        ALL("все", { true }),
        LISTENED("со слушателями", { it.listeners > 0 }),
        CALLED("вызывались", { it.calls > 0 }),
        DENIED("с отказами", { it.denied > 0 }),
        TODAY("сегодня", { info -> info.lastCalledAt?.atZone(DisplayZone.zone)?.toLocalDate() == LocalDate.now(DisplayZone.zone) }),
    }

    private class Row(val stack: ItemStack, val click: MenuClickHandler? = null)

    /** Opens the menu for [sender], or explains that the console has no inventory. */
    fun openFor(sender: NativeSender, reply: Reply) {
        if (sender is Player) home(sender)
        else replies.say(reply, ChatTone.INFO, "Меню доступно только игроку.", "В консоли используйте /pn updates.")
    }

    // --- screens -------------------------------------------------------------------------------------------------

    /** The home page: counters and the way to every other screen. */
    fun home(player: Player) {
        val snapshots = snapshots()
        val pending = snapshots.count { it.state in CatalogLabels.PENDING }
        val errors = library.journalGroups("error", JOURNAL_HOURS, JOURNAL_LIMIT).size
        val problems = snapshots.count { it.state in CatalogLabels.PROBLEMS } + errors
        val plan = library.updates.currentPlan().orElse(null)
        val builder = frame(Menus.chest(title("Главная")).rows(HOME_ROWS), HOME_ROWS)
        builder.slot(
            4,
            icon(
                Icons.OVERVIEW, "NETHER_STAR", "${PnLibraryIdentity.NAME} ${library.version}",
                Style.lore(
                    listOf("Библиотека для плагинов pnFolder: обновления, настройки и поддержка."),
                    listOf(
                        "Версия" to library.version,
                        "Итог" to when {
                            problems > 0 -> "✖ проблем: $problems"
                            pending > 0 -> "✦ к обновлению: $pending"
                            else -> "✔ всё актуально"
                        },
                        "Плагинов" to "${connectedCount()}",
                        "Обновлений" to "$pending",
                        "Проблем" to "$problems",
                    ),
                ),
            ),
        )
        builder.button(11,
            section(Icons.PLUGINS, "BOOKSHELF", "Плагины", "Все плагины, подключённые к библиотеке.", "Всего" to "${connectedCount()}"),
            go { plugins(it, Filter.ALL, 0) })
        builder.button(13,
            section(Icons.UPDATES, "GOLD_INGOT", "Обновления", "Плагины, для которых вышла новая версия.", "Доступно" to "$pending"),
            go { plugins(it, Filter.UPDATES, 0) })
        builder.button(15,
            section(Icons.PROBLEMS, "BARRIER", "Проблемы", "Ошибки проверки и блокировки обновлений.", "Найдено" to "$problems"),
            go { plugins(it, Filter.PROBLEMS, 0) })
        builder.button(29, section(Icons.HISTORY, "BOOK", "История", "Проверки, обновления, откаты и ошибки."), go { history(it, null, 0) })
        builder.button(
            30,
            section(
                Icons.PROBLEMS, "BOOK", "Журнал сервера", "Ошибки и предупреждения сервера за последние сутки.",
                "Ошибок" to if (errors > 0) "✖ $errors" else "✔ нет",
            ),
            go { journal(it, null, 0) },
        )
        builder.button(
            32,
            section(Icons.CLOCK, "CLOCK", "Задачи плагинов", "Что плагины делают по расписанию и для чего.", "Всего" to "${library.taskSnapshots().size}"),
            go { allowed(it, TASKS) { tasks(it, TaskPeriod.TODAY, TaskState.ALL, 0) } },
        )
        builder.button(
            31,
            section(Icons.UPDATES, "REDSTONE_TORCH", "События плагинов", "Как плагины общаются друг с другом: слушатели, вызовы, ошибки."),
            go { allowed(it, BUS) { busHome(it) } },
        )
        builder.button(33, section(Icons.BACKUPS, "CHEST", "Резервные копии", "Откат к сохранённым версиям плагинов."), go { backups(it, 0) })
        builder.button(
            24,
            icon(Icons.CHECK, "COMPASS", "Проверить всё", Style.lore(listOf("Спросить каталоги релизов прямо сейчас."), emptyList(), Style.left("проверить"))),
            go { checkAll(it) },
        )
        builder.button(20, section(Icons.SETTINGS, "REDSTONE", "Настройки", "Автозагрузка и пауза для всех плагинов сразу."), go { settings(it) })
        builder.button(22, section(Icons.TOOLS, "ANVIL", "Инструменты", "Статус сервера, отчёт для поддержки, валюты."), go { tools(it) })
        builder.button(
            SUPPORT_SLOT,
            icon(Icons.SUPPORT, "PAPER", "Поддержка", Style.lore(listOf("Если возникла проблема, мы поможем."), emptyList(), Style.left("получить ссылку"))),
            go { support(it) },
        )
        builder.button(HOME_CLOSE, closeItem(), go { it.closeInventory() })
        if (plan != null && plan.state == UpdateState.UPDATE_STAGED) {
            val restart = icon(
                Icons.RESTART, "DIAMOND", "Перезапуск",
                Style.lore(listOf("Обновления подготовлены."), emptyList(), Style.left("подтвердить в чате")),
            )
            builder.button(RESTART_SLOT, restart, go { it.closeInventory(); it.performCommand("pn update restart") })
        }
        if (plan?.plan?.changes?.isNotEmpty() == true) {
            val rollback = icon(
                Icons.ROLLBACK, "TNT", "Откатить план",
                Style.lore(listOf("Откатит все связанные плагины плана."), emptyList(), Style.left("подтвердить в чате")),
                danger = true,
            )
            builder.button(ROLLBACK_SLOT, rollback, go { it.closeInventory(); it.performCommand("pn update rollback") })
        }
        show(player, builder.build())
    }

    private fun plugins(player: Player, filter: Filter, page: Int) {
        val updatable = snapshots()
        val shown = updatable.filter {
            when (filter) {
                Filter.ALL -> true
                Filter.UPDATES -> it.state in CatalogLabels.PENDING
                Filter.PROBLEMS -> it.state in CatalogLabels.PROBLEMS
            }
        }.map { snapshot ->
            Row(
                plate(snapshot, hints = true),
                goClick { who, click ->
                    when (click) {
                        ClickType.RIGHT -> checkOne(who, snapshot.product)
                        ClickType.SHIFT_LEFT -> allowed(who, SETTINGS) { toggleAuto(who, snapshot) }
                        else -> card(who, snapshot.product)
                    }
                },
            )
        }
        val rows = when (filter) {
            Filter.ALL -> shown + unmanaged(updatable.map { it.product.lowercase() }.toSet())
            Filter.PROBLEMS -> shown + journalRows("error") { who -> plugins(who, filter, page) }
            Filter.UPDATES -> shown
        }
        list(player, filter.title, rows, page, PAGE_SIZE, { who, to -> plugins(who, filter, to) }, { home(it) }, "Здесь пусто")
    }

    private fun card(player: Player, product: String) {
        val registration = library.updates.get(product) ?: return home(player)
        val snapshot = registration.snapshot
        val builder = frame(Menus.chest(title(snapshot.product)).rows(CARD_ROWS), CARD_ROWS)
        builder.slot(4, plate(snapshot, withHint = false))
        val update = if (snapshot.state == UpdateState.UPDATE_AVAILABLE) {
            icon(
                null, "EMERALD", "Обновить",
                Style.lore(
                    listOf("Подготовить версию ${snapshot.latestVersion}.", "Применится после перезапуска."),
                    emptyList(), Style.left("подготовить"),
                ),
            )
        } else {
            icon(null, "COAL", "Обновить", Style.lore(listOf("Сейчас нет доступного обновления."), emptyList()), muted = true)
        }
        builder.button(10, update, go { allowed(it, DOWNLOAD) { it.closeInventory(); it.performCommand("pn update ${snapshot.product}") } })
        builder.button(
            11,
            icon(Icons.CHECK, "COMPASS", "Проверить", Style.lore(listOf("Проверить обновления этого плагина."), emptyList(), Style.left("проверить"))),
            go { checkOne(it, registration.snapshot.product) },
        )
        builder.button(12, autoItem(snapshot), go { who -> allowed(who, SETTINGS) { toggleAuto(who, snapshot) } })
        if (CHANNEL_SWITCH) {
            builder.button(
                13,
                section(Icons.SETTINGS, "LEVER", "Канал", "Какие версии предлагать.", "Сейчас" to snapshot.channel.name.lowercase()),
                go { channels(it, snapshot.product) },
            )
        } else {
            builder.slot(
                13,
                icon(
                    Icons.LOCKED, "BARRIER", "Канал обновлений",
                    Style.lore(
                        listOf("Сейчас работает только канал dev, остальные пока недоступны."),
                        listOf("Канал" to snapshot.channel.name.lowercase()),
                    ),
                    muted = true,
                ),
            )
        }
        builder.button(14, holdItem(snapshot), go { holds(it, snapshot.product) })
        builder.button(
            15,
            section(Icons.RELEASES, "BOOKSHELF", "Релизы", "Известные версии плагина.", "Найдено" to "${snapshot.availableReleases.size}"),
            go { releases(it, snapshot.product, 0) },
        )
        builder.button(16, section(Icons.HISTORY, "BOOK", "История", "Записи только этого плагина."), go { history(it, snapshot.product, 0) })
        snapshot.latestChangelogUrl?.let { link ->
            builder.button(
                22,
                icon(Icons.CHANGELOG, "WRITTEN_BOOK", "Список изменений",
                    Style.lore(listOf("Что нового в последней версии."), emptyList(), Style.left("получить ссылку"))),
                go { who -> who.closeInventory(); tell(who, ChatTone.INFO, "Список изменений ${snapshot.product}", link) },
            )
        }
        builder.button(CARD_BACK, backItem(), go { plugins(it, Filter.ALL, 0) })
        show(player, builder.build())
    }

    /** The channel choice. It stays in the code and is switched on by [CHANNEL_SWITCH] once more than `dev` works. */
    private fun channels(player: Player, product: String) {
        val current = library.updates.get(product)?.snapshot?.channel
        val builder = frame(Menus.chest(title("Канал $product")).rows(SMALL_ROWS), SMALL_ROWS)
        UpdateChannel.values().forEachIndexed { index, channel ->
            val chosen = channel == current
            val lore = Style.lore(
                listOf(CHANNEL_HINTS.getValue(channel)),
                if (chosen) listOf("Состояние" to "выбран сейчас") else emptyList(),
                *if (chosen) emptyArray() else arrayOf(Style.left("выбрать")),
            )
            builder.button(
                10 + index,
                icon(null, CHANNEL_ITEMS.getValue(channel), channel.name.lowercase(), lore, good = chosen),
                go { who -> allowed(who, SETTINGS) { chooseChannel(who, product, channel) } },
            )
        }
        builder.button(
            16,
            icon(
                Icons.RESET, "BARRIER", "Сбросить",
                Style.lore(listOf("Вернуть канал, который указал плагин."), emptyList(), Style.left("сбросить")), muted = true,
            ),
            go { who -> allowed(who, SETTINGS) { chooseChannel(who, product, null) } },
        )
        builder.button(SMALL_BACK, backItem(), go { card(it, product) })
        show(player, builder.build())
    }

    private fun holds(player: Player, product: String) {
        val until = library.updates.heldUntil(product)?.takeIf { it.isAfter(Instant.now()) }
        val remaining = until?.let { Duration.between(Instant.now(), it) }
        val chosen = remaining?.let { holdIndex(it) } ?: -1
        val builder = frame(Menus.chest(title("Пауза $product")).rows(SMALL_ROWS), SMALL_ROWS)
        builder.slot(
            4,
            icon(
                Icons.CLOCK, "CLOCK", "Пауза сейчас",
                Style.lore(
                    listOf("Пока пауза идёт, обновления не предлагаются и не скачиваются."),
                    listOf("Состояние" to (remaining?.let { "‖ осталось ${left(it)}" } ?: "нет паузы")),
                ),
                alternative = "WATCH", warn = remaining != null,
            ),
        )
        HOLDS.forEachIndexed { index, (label, duration) ->
            val selected = index == chosen
            val note = if (selected) listOf("Выбрано" to "● осталось ${left(remaining!!)}") else emptyList()
            builder.button(
                10 + index,
                icon(
                    Icons.CLOCK, "CLOCK", label,
                    Style.lore(listOf("Приостановить обновления на этот срок."), note, Style.left(if (selected) "начать срок заново" else "приостановить")),
                    alternative = "WATCH", good = selected,
                ),
                go { who -> allowed(who, SETTINGS) { chooseHold(who, product, duration) } },
            )
        }
        if (remaining != null) {
            builder.button(
                16,
                icon(Icons.RESET, "REDSTONE", "Снять паузу", Style.lore(listOf("Вернуть обновления сейчас."), emptyList(), Style.left("снять")), good = true),
                go { who -> allowed(who, SETTINGS) { chooseHold(who, product, null) } },
            )
        }
        builder.button(SMALL_BACK, backItem(), go { card(it, product) })
        show(player, builder.build())
    }

    /** Grey lines under the pause choices: how many plugins the pause reaches and how many have no update registration. */
    private fun pauseFootnote(registered: Int, unregistered: Int): List<String> = buildList {
        add("")
        add(Style.description("Пауза касается плагинов с обновлениями: $registered."))
        if (unregistered > 0) add(Style.description("Без регистрации обновлений: $unregistered, их пауза не касается."))
    }

    /** The index of the shortest pause in [HOLDS] that is at least [remaining]; the last one when none is. */
    private fun holdIndex(remaining: Duration): Int = HOLDS.indexOfFirst { it.second >= remaining }.takeIf { it >= 0 } ?: (HOLDS.size - 1)

    /** A time span in words: `1 д 4 ч`, `5 ч 12 мин`, `23 мин`. */
    private fun left(span: Duration): String {
        val days = span.toDays()
        val hours = span.toHours() % HOURS_PER_DAY
        val minutes = span.toMinutes() % MINUTES_PER_HOUR
        return when {
            days > 0 -> "$days д $hours ч"
            hours > 0 -> "$hours ч $minutes мин"
            else -> "${minutes.coerceAtLeast(1)} мин"
        }
    }

    private fun releases(player: Player, product: String, page: Int) {
        val snapshot = library.updates.get(product)?.snapshot ?: return home(player)
        val rows = snapshot.availableReleases.map { release ->
            val current = release.version == snapshot.currentVersion
            val link = release.changelogUrl
            val parameters = buildList {
                add("Канал" to release.channel.name.lowercase())
                release.publishedAt?.let { add("Выпущен" to time(it)) }
                if (current) add("Состояние" to "установлена сейчас")
            }
            val lore = Style.lore(emptyList(), parameters, *if (link != null) arrayOf(Style.left("получить список изменений")) else emptyArray())
            val click = link?.let { url -> go { who -> who.closeInventory(); tell(who, ChatTone.INFO, "Список изменений ${release.version}", url) } }
            Row(icon(null, if (current) "EMERALD" else "PAPER", release.version, lore, good = current), click)
        }
        list(player, "Релизы $product", rows, page, PAGE_SIZE, { who, to -> releases(who, product, to) }, { card(it, product) }, "Релизов пока нет")
    }

    private fun history(player: Player, product: String?, page: Int) {
        val normalized = product?.lowercase()
        library.updates.historyCount(normalized).thenCompose { total ->
            library.updates.historyPage(page + 1, normalized).thenApply { total to it }
        }.whenComplete { result, error ->
            replies.onServerThread {
                if (!player.isOnline) return@onServerThread
                val (total, entries) = result ?: (0 to emptyList())
                val back: (Player) -> Unit = if (product == null) { who -> home(who) } else { who -> card(who, product) }
                val rows = if (error != null) {
                    emptyList()
                } else {
                    entries.map { entry -> Row(historyItem(entry), go { historyEntry(it, entry) { who -> history(who, product, page) } }) }
                }
                val title = if (product == null) "История" else "История $product"
                list(player, title, rows, page, HISTORY_PAGE, { who, to -> history(who, product, to) }, back, "Записей пока нет", total)
            }
        }
    }

    private fun backups(player: Player, page: Int) {
        library.updates.backups().whenComplete { all, error ->
            replies.onServerThread {
                if (!player.isOnline) return@onServerThread
                val rows = if (error != null) {
                    emptyList()
                } else {
                    all.sortedByDescending { it.createdAt }.map { backup -> Row(backupItem(backup), go { backupChoice(it, backup) }) }
                }
                list(player, "Резервные копии", rows, page, PAGE_SIZE, { who, to -> backups(who, to) }, { home(it) }, "Резервных копий нет")
            }
        }
    }

    private fun backupChoice(player: Player, backup: UpdateBackup) {
        val builder = frame(Menus.chest(title("Откат")).rows(SMALL_ROWS), SMALL_ROWS)
        builder.slot(4, backupItem(backup, withHint = false))
        val plain = Style.lore(
            listOf("Вернутся сохранённые JAR.", "Конфигурации не изменятся.", "Применится после перезапуска."),
            emptyList(), Style.left("откатить"),
        )
        builder.button(11, icon(Icons.ROLLBACK, "TNT", "Откатить плагины", plain, danger = true), go { who ->
            allowed(who, ROLLBACK) { rollback(who, backup, restoreConfigurations = false) }
        })
        if (backup.configurationFiles > 0) {
            val full = Style.lore(
                listOf("Вернутся JAR и файлы настроек: ${backup.configurationFiles}.", "Применится после перезапуска."),
                emptyList(), Style.left("откатить"),
            )
            builder.button(13, icon(Icons.ROLLBACK, "TNT", "Откатить плагины и настройки", full, danger = true), go { who ->
                allowed(who, ROLLBACK) { rollback(who, backup, restoreConfigurations = true) }
            })
        }
        builder.button(SMALL_BACK, backItem("Отмена"), go { backups(it, 0) })
        show(player, builder.build())
    }

    /** The journal of the last day: equal events merged, each opening its card in the chat. */
    private fun journal(player: Player, level: String?, page: Int) {
        val title = "Журнал сервера" + (level?.let { " " + LEVELS.getValue(it) } ?: "")
        val next = LEVEL_CYCLE[(LEVEL_CYCLE.indexOf(level) + 1) % LEVEL_CYCLE.size]
        val cycle = icon(
            Icons.SETTINGS, "HOPPER", "Уровень: ${level?.let { LEVELS.getValue(it) } ?: "все"}",
            Style.lore(
                listOf("Какие события показывать."), emptyList(), Style.left("переключить"),
                extra = Style.choices(LEVEL_CYCLE.map { it?.let { name -> LEVELS.getValue(name) } ?: "все" }, LEVEL_CYCLE.indexOf(level)),
            ),
        )
        list(
            player, title, journalRows(level) { who -> journal(who, level, page) }, page, PAGE_SIZE,
            { who, to -> journal(who, level, to) }, { home(it) }, "За сутки событий нет",
            extra = { it.button(FILTER, cycle, go { who -> journal(who, next, 0) }) },
        )
    }

    private fun journalRows(level: String?, back: (Player) -> Unit): List<Row> =
        library.journalGroups(level, JOURNAL_HOURS, JOURNAL_LIMIT).map { group ->
            Row(
                journalItem(group),
                goClick { who, click ->
                    when (click) {
                        ClickType.RIGHT -> chat(who, "pn events times ${group.id}")
                        ClickType.SHIFT_LEFT -> chat(who, "pn events full ${group.id}")
                        else -> journalEvent(who, group.id, back)
                    }
                },
            )
        }

    private fun chat(player: Player, command: String) {
        player.closeInventory()
        player.performCommand(command)
    }

    private fun journalItem(group: JournalGroup): ItemStack {
        val material = journalMaterial(group.level)
        val parameters = listOf(
            "Источник" to group.source,
            "Когда" to time(Instant.ofEpochMilli(group.lastMillis)),
            "Повторов" to "${group.count}",
        )
        val lore = Style.lore(
            listOf(group.message.take(MESSAGE_PREVIEW)), parameters,
            Style.left("открыть событие"), Style.right("все повторы в чат"), Style.shiftLeft("полный текст в чат"),
        )
        return icon(null, material, "${LEVELS.getValue(group.level)}: ${group.source}", lore, danger = group.level == "error", warn = group.level == "warning")
    }

    private fun settings(player: Player) {
        val all = snapshots()
        val automatic = all.count { it.automaticDownload }
        val everyoneAuto = all.isNotEmpty() && automatic == all.size
        val untils = all.mapNotNull { library.updates.heldUntil(it.product) }.filter { it.isAfter(Instant.now()) }
        val longest = untils.maxOrNull()?.let { Duration.between(Instant.now(), it) }
        val pauseChoices = listOf("нет паузы") + HOLDS.take(CYCLE_HOLDS).map { "на ${it.first}" }
        val pauseSelected = longest?.let { span ->
            1 + (HOLDS.take(CYCLE_HOLDS).indexOfFirst { it.second >= span }.takeIf { it >= 0 } ?: (CYCLE_HOLDS - 1))
        } ?: 0
        val builder = frame(Menus.chest(title("Настройки")).rows(SMALL_ROWS + 1), SMALL_ROWS + 1)
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
                    everyone(who, done) { setAutomatic(it, !everyoneAuto, who) }
                }
            },
        )
        val pauseNote = buildList {
            if (untils.isNotEmpty()) add("Осталось" to left(longest!!))
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
            go { who ->
                allowed(who, SETTINGS) {
                    val nextIndex = (pauseSelected + 1) % pauseChoices.size
                    val duration = HOLDS.take(CYCLE_HOLDS).getOrNull(nextIndex - 1)
                    val done = if (duration == null) "Все паузы сняты." else "Обновления всех плагинов приостановлены: ${duration.first}."
                    everyone(who, done) { hold(it, duration?.second, who) }
                }
            },
        )
        builder.button(SMALL_BACK + 9, backItem(), go { home(it) })
        show(player, builder.build())
    }

    private fun tools(player: Player) {
        val builder = frame(Menus.chest(title("Инструменты")).rows(SMALL_ROWS + 1), SMALL_ROWS + 1)
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
                go { who -> who.closeInventory(); who.performCommand(command) },
            )
        }
        builder.button(
            SUPPORT_TOOL_SLOT,
            icon(Icons.SUPPORT, "PAPER", "Поддержка", Style.lore(listOf("Ссылка на помощь."), emptyList(), Style.left("получить"))),
            go { support(it) },
        )
        builder.button(SMALL_BACK + 9, backItem(), go { home(it) })
        show(player, builder.build())
    }

    // --- journal event and history entry -------------------------------------------------------------------------

    /** One journal event with its text, stack trace, repeats and facts; [back] returns to the list it was opened from. */
    private fun journalEvent(player: Player, id: String, back: (Player) -> Unit) {
        val detail = library.journalDetail(id)
        if (detail == null) {
            tell(player, ChatTone.INFO, "Это событие уже вышло из журнала.")
            return back(player)
        }
        val builder = frame(Menus.chest(title("Событие")).rows(DETAIL_ROWS), DETAIL_ROWS)
        val error = detail.level == "error"
        val summary = Style.lore(
            wrap(detail.message),
            listOf(
                "Важность" to LEVELS.getValue(detail.level),
                "Источник" to detail.source,
                "Повторов" to "${detail.times.size}",
                "Последний раз" to stamp(Instant.ofEpochMilli(detail.times.first())),
                "" to ago(Instant.ofEpochMilli(detail.times.first())),
            ),
        )
        builder.slot(4, icon(null, journalMaterial(detail.level), "Событие ${detail.source}", summary, danger = error, warn = detail.level == "warning"))
        detail.stackTrace?.let { trace ->
            val lore = Style.lore(emptyList(), emptyList(), Style.left("весь текст в чат"), extra = wrap(trace, limit = TRACE_LINES).map { Style.danger(it) })
            builder.button(
                20,
                icon(Icons.PROBLEMS, "BOOK", "Текст ошибки", lore, danger = true),
                go { who -> who.closeInventory(); who.performCommand("pn events full $id") },
            )
        }
        val moments = detail.times.map { Instant.ofEpochMilli(it) }
        val more = moments.size - TIMES_PREVIEW
        val timesLore = Style.lore(
            moments.take(TIMES_PREVIEW).map { stamp(it) },
            listOf("Пояс" to DisplayZone.zone.id),
            *if (more > 0) arrayOf(Style.left("все ${moments.size} раз")) else emptyArray(),
            extra = if (more > 0) listOf(Style.description("и ещё $more")) else emptyList(),
        )
        builder.button(
            22, icon(Icons.CLOCK, "CLOCK", "Когда случалось", timesLore, alternative = "WATCH"),
            go { who -> if (more > 0) journalTimes(who, id, 0, back) },
        )
        if (detail.details.isNotEmpty()) {
            val facts = detail.details.entries.take(DETAILS_SHOWN).map { (key, value) -> key to value.take(MESSAGE_PREVIEW) }
            builder.slot(24, icon(Icons.CHANGELOG, "PAPER", "Подробности", Style.lore(emptyList(), facts)))
        }
        builder.button(
            30,
            icon(
                Icons.CHECK, "COMPASS", "Показать в чате",
                Style.lore(listOf("То же самое, что команда /pn events view."), emptyList(), Style.left("открыть в чате")),
            ),
            go { who -> who.closeInventory(); who.performCommand("pn events view $id") },
        )
        builder.button(DETAIL_BACK, backItem(), go { back(it) })
        show(player, builder.build())
    }

    /** Every time a journal event happened, newest first, in the configured time zone. */
    private fun journalTimes(player: Player, id: String, page: Int, back: (Player) -> Unit) {
        val detail = library.journalDetail(id)
        if (detail == null) {
            tell(player, ChatTone.INFO, "Это событие уже вышло из журнала.")
            return back(player)
        }
        val rows = detail.times.mapIndexed { index, millis ->
            val moment = Instant.ofEpochMilli(millis)
            val lore = Style.lore(emptyList(), listOf("Было" to ago(moment), "Номер" to "${detail.times.size - index} из ${detail.times.size}"))
            Row(icon(Icons.CLOCK, "CLOCK", stamp(moment), lore, alternative = "WATCH"))
        }
        list(player, "Когда случалось", rows, page, PAGE_SIZE, { who, to -> journalTimes(who, id, to, back) }, { journalEvent(it, id, back) }, "Времени нет")
    }

    /** One entry of the update history with the whole message and a button for every plugin it touched. */
    private fun historyEntry(player: Player, entry: UpdateHistoryEntry, back: (Player) -> Unit) {
        val builder = frame(Menus.chest(title("Запись истории")).rows(DETAIL_ROWS), DETAIL_ROWS)
        builder.slot(4, historyItem(entry, full = true))
        entry.changes.take(CHANGE_BUTTONS).forEachIndexed { index, change ->
            val product = change.product.value
            val known = library.updates.get(product) != null
            val lore = Style.lore(
                listOf("Версия изменилась."),
                listOf("Было" to "${change.from}", "Стало" to "${change.to}"),
                *if (known) arrayOf(Style.left("открыть карточку")) else emptyArray(),
            )
            val stack = icon(Icons.PLUGIN, "PAPER", product, lore, muted = !known)
            if (known) builder.button(CHANGE_FIRST + index, stack, go { card(it, product) }) else builder.slot(CHANGE_FIRST + index, stack)
        }
        builder.button(DETAIL_BACK, backItem(), go { back(it) })
        show(player, builder.build())
    }

    // --- tasks ---------------------------------------------------------------------------------------------------

    /** Every task of every plugin, newest first, with two switches that show all their choices: the date and the state. */
    private fun tasks(player: Player, period: TaskPeriod, state: TaskState, page: Int) {
        val nextPeriod = TaskPeriod.values()[(period.ordinal + 1) % TaskPeriod.values().size]
        val nextState = TaskState.values()[(state.ordinal + 1) % TaskState.values().size]
        val all = library.taskSnapshots()
        val inPeriod = all.filter(period::keep)
        val rows = inPeriod.filter(state::keep).sortedByDescending { TaskLabels.activity(it) }.map { task ->
            Row(taskItem(task), go { who -> taskDetail(who, task.id.value) { tasks(it, period, state, page) } })
        }
        val byDate = icon(
            Icons.CLOCK, "CLOCK", "Когда",
            Style.lore(
                listOf("Задачи за какое время показывать."), emptyList(), Style.left("переключить"),
                extra = Style.choices(TaskPeriod.values().map { "${it.label} ${all.count(it::keep)}" }, period.ordinal),
            ),
            alternative = "WATCH",
        )
        val byState = icon(
            Icons.SETTINGS, "HOPPER", "Какие",
            Style.lore(
                listOf("Какие задачи показывать."), emptyList(), Style.left("переключить"),
                extra = Style.choices(TaskState.values().map { "${it.label} ${inPeriod.count(it::keep)}" }, state.ordinal),
            ),
        )
        list(
            player, "Задачи: ${period.label}, ${state.label}", rows, page, PAGE_SIZE, { who, to -> tasks(who, period, state, to) }, { home(it) },
            "Задач нет",
            extra = {
                it.button(DATE_FILTER, byDate, go { who -> tasks(who, nextPeriod, state, 0) })
                it.button(FILTER, byState, go { who -> tasks(who, period, nextState, 0) })
            },
        )
    }

    private fun taskItem(task: TaskSnapshot): ItemStack {
        val material = when (task.status) {
            TaskStatus.SCHEDULED -> "CLOCK"
            TaskStatus.RUNNING -> "EMERALD"
            TaskStatus.COMPLETED -> "PAPER"
            TaskStatus.CANCELLED -> "COAL"
            TaskStatus.FAILED -> "BARRIER"
        }
        val description = task.description?.let { wrap(it, limit = 3) } ?: listOf("Плагин не описал эту задачу.")
        val parameters = buildList {
            add("Плагин" to ownerLine(task.ownerName))
            add("Состояние" to TaskLabels.label(task.status))
            add("Запусков" to "${task.runCount}")
            task.lastFailure?.let { add("Ошибка" to "✖ ${it.take(MESSAGE_PREVIEW)}") }
        }
        val lore = Style.lore(description, parameters, Style.left("подробности"))
        return icon(
            null, material, TaskLabels.title(task), lore, alternative = "WATCH".takeIf { material == "CLOCK" },
            danger = task.status == TaskStatus.FAILED, good = task.status == TaskStatus.RUNNING, muted = task.status == TaskStatus.CANCELLED,
        )
    }

    /** One task: what it is for, who owns it, how it runs, when; waiting tasks can be started, active ones stopped. */
    private fun taskDetail(player: Player, id: String, back: (Player) -> Unit) {
        val task = library.taskSnapshots().firstOrNull { it.id.value == id }
        if (task == null) {
            tell(player, ChatTone.INFO, "Эта задача уже исчезла из списка.")
            return back(player)
        }
        val builder = frame(Menus.chest(title("Задача")).rows(DETAIL_ROWS), DETAIL_ROWS)
        val summary = buildList {
            add("Плагин" to ownerLine(task.ownerName))
            add("Состояние" to TaskLabels.label(task.status))
            task.interval?.let { add("Повторяется" to "каждые ${TaskLabels.span(it)}") }
        }
        val about = task.description?.let { wrap(it) } ?: listOf("Плагин не описал эту задачу.")
        builder.slot(
            4,
            icon(
                null, "CLOCK", TaskLabels.title(task), Style.lore(about, summary), alternative = "WATCH",
                danger = task.status == TaskStatus.FAILED, good = task.status == TaskStatus.RUNNING,
            ),
        )
        val usage = task.usage?.let { wrap(it) } ?: listOf("Не указано. Плагин может добавить usage в TaskSpec.")
        builder.slot(20, icon(Icons.CHANGELOG, "WRITTEN_BOOK", "Как используется", Style.lore(usage, emptyList()), muted = task.usage == null))
        val runs = buildList {
            add("Запусков" to "${task.runCount}")
            add("Пропущено" to "${task.skippedCount}")
            task.lastDuration?.let { add("Последний заняла" to TaskLabels.span(it)) }
            add("Где" to TaskLabels.where(task))
        }
        builder.slot(21, icon(Icons.SETTINGS, "COMPARATOR", "Как выполняется", Style.lore(emptyList(), runs)))
        val times = buildList {
            add("Создана" to stamp(task.createdAt))
            task.nextRunAt?.let { add("Следующий запуск" to stamp(it)); add("" to ago(it)) }
            task.lastStartedAt?.let { add("Последний запуск" to stamp(it)); add("" to ago(it)) }
            task.lastCompletedAt?.let { add("Закончилась" to stamp(it)) }
            add("Пояс" to DisplayZone.zone.id)
        }
        builder.slot(22, icon(Icons.CLOCK, "CLOCK", "Время", Style.lore(emptyList(), times), alternative = "WATCH"))
        val origin = buildList {
            task.source?.let { addAll(labelled("Класс", it)) }
            task.key?.let { add("Ключ" to it) }
            if (task.tags.isNotEmpty()) addAll(labelled("Теги", task.tags.joinToString(", ")))
            add("Остановить" to TaskLabels.stopAnswer(task))
            add("Запустить сейчас" to TaskLabels.runAnswer(task))
            add("Номер" to task.id.value.take(TaskLabels.SHORT_ID))
        }
        builder.slot(23, icon(Icons.PLUGIN, "PAPER", "Откуда и права", Style.lore(emptyList(), origin)))
        task.lastFailure?.let { failure ->
            val lore = Style.lore(emptyList(), emptyList(), extra = wrap(failure).map { Style.danger(it) })
            builder.slot(24, icon(Icons.PROBLEMS, "BARRIER", "Последняя ошибка", lore, danger = true))
        }
        if (!(task.status == TaskStatus.SCHEDULED && task.runnableNow)) {
            builder.slot(
                29,
                icon(
                    Icons.PROBLEMS, "BARRIER", "Запуск закрыт",
                    Style.lore(listOf(TaskLabels.runAnswer(task).replaceFirstChar { it.uppercase() } + "."), emptyList()),
                    muted = true,
                ),
            )
        }
        if (task.status == TaskStatus.SCHEDULED && task.runnableNow) {
            builder.button(
                29,
                icon(
                    Icons.CHECK, "EMERALD", "Запустить сейчас",
                    Style.lore(listOf("Выполнить один раз, не дожидаясь срока. Расписание не меняется."), emptyList(), Style.left("запустить")), good = true,
                ),
                go { who ->
                    allowed(who, TASKS) {
                        when (library.runTaskNow(task.id.value)) {
                            TaskControl.DONE -> tell(who, ChatTone.SUCCESS, "Задача запущена.")
                            TaskControl.FORBIDDEN -> tell(who, ChatTone.FAILURE, "Плагин не разрешает запускать эту задачу вручную.")
                            TaskControl.UNAVAILABLE -> tell(who, ChatTone.INFO, "Сейчас запустить нельзя: задача не ждёт срока.")
                        }
                        taskDetail(who, id, back)
                    }
                },
            )
        }
        if (!TaskLabels.isActive(task.status)) {
            builder.slot(
                31,
                icon(
                    Icons.PROBLEMS, "BARRIER", "Остановка закрыта",
                    Style.lore(listOf(TaskLabels.stopAnswer(task).replaceFirstChar { it.uppercase() } + "."), emptyList()), muted = true,
                ),
            )
        }
        if (TaskLabels.isActive(task.status)) {
            if (task.cancellable) {
                builder.button(
                    31,
                    icon(
                        Icons.ROLLBACK, "TNT", "Остановить задачу",
                        Style.lore(listOf("Больше не будет запускаться."), emptyList(), Style.left("остановить")), danger = true,
                    ),
                    go { who ->
                        allowed(who, TASKS) {
                            when (library.cancelTask(task.id.value)) {
                                TaskControl.DONE -> tell(who, ChatTone.SUCCESS, "Задача остановлена.")
                                TaskControl.FORBIDDEN -> tell(who, ChatTone.FAILURE, "Плагин не разрешает останавливать эту задачу.")
                                TaskControl.UNAVAILABLE -> tell(who, ChatTone.INFO, "Задача уже не работает.")
                            }
                            taskDetail(who, id, back)
                        }
                    },
                )
            } else {
                builder.slot(
                    31,
                    icon(
                        Icons.PROBLEMS, "BARRIER", "Остановка закрыта",
                        Style.lore(listOf("Плагин запретил останавливать эту задачу из меню и команд."), emptyList()), muted = true,
                    ),
                )
            }
        }
        builder.button(DETAIL_BACK, backItem(), go { back(it) })
        show(player, builder.build())
    }

    // --- plugins that own things ---------------------------------------------------------------------------------

    /** The Bukkit plugin whose class loader loaded [type], or `null` for the server and library classes. */
    private fun pluginOf(type: Class<*>?): Plugin? {
        val loader = type?.classLoader ?: return null
        return library.plugins.all().mapNotNull { it.owner as? Plugin }.firstOrNull { it.javaClass.classLoader === loader }
    }

    private fun pluginByName(name: String): Plugin? =
        runCatching { plugin.server.pluginManager.getPlugin(name) }.getOrNull()
            ?: library.plugins.all().mapNotNull { it.owner as? Plugin }.firstOrNull { it.javaClass.simpleName == name || it.name.equals(name, true) }

    /** `Name 1.2.3` for a plugin, or the plain [name] when the server does not know it. */
    private fun ownerLine(name: String): String = pluginByName(name)?.let { "${it.name} ${it.description.version}" } ?: name

    /** The lines that say which plugin owns something: its name, version and authors. */
    private fun ownerParameters(owner: Plugin?): List<Pair<String, String>> = owner?.let {
        buildList {
            add("Плагин" to it.name)
            add("Версия" to it.description.version)
            if (it.description.authors.isNotEmpty()) add("Автор" to it.description.authors.joinToString(", "))
        }
    } ?: listOf("Плагин" to "сервер или библиотека")

    /** [text] as `[ label ]` followed by indented lines, for a value that does not fit one line. */
    private fun labelled(label: String, text: String): List<Pair<String, String>> =
        wrap(text).mapIndexed { index, line -> (if (index == 0) label else "") to line }

    // --- plugin events (the event bus) ---------------------------------------------------------------------------

    private val bus get() = library.events.inspector

    /** The start page of the plugin events: what they are, the counters and the way to every view. */
    private fun busHome(player: Player) {
        val events = bus.events()
        val listeners = bus.listeners()
        val today = LocalDate.now(DisplayZone.zone)
        val calls = bus.history().filter { it.date == today }.sumOf { it.calls }
        val failures = listeners.sumOf { it.failures }
        val traced = bus.tracedEvents().size
        val builder = frame(Menus.chest(title("События плагинов")).rows(HOME_ROWS), HOME_ROWS)
        builder.slot(
            4,
            icon(
                Icons.OVERVIEW, "NETHER_STAR", "События плагинов",
                Style.lore(
                    wrap("Так плагины сообщают друг другу, что произошло. Одни вызывают событие, другие слушают его и отвечают."),
                    listOf(
                        "Событий" to "${events.size}",
                        "Слушателей" to "${listeners.size}",
                        "Вызовов сегодня" to "$calls",
                        "Ошибок" to if (failures > 0) "✖ $failures" else "✔ нет",
                    ),
                ),
            ),
        )
        builder.button(
            20, section(Icons.UPDATES, "REDSTONE_TORCH", "Все события", "Какие события есть и как часто их вызывают.", "Всего" to "${events.size}"),
            go { busEvents(it, BusFilter.ALL, 0) },
        )
        builder.button(
            22, section(Icons.PLUGINS, "COMPARATOR", "Слушатели", "Кто и на какие события подписан.", "Всего" to "${listeners.size}"),
            go { busListeners(it, ListenerQuery("Слушатели", { who -> busHome(who) }), 0) },
        )
        builder.button(
            24, section(Icons.RELEASES, "BOOKSHELF", "По плагинам", "Слушатели, сгруппированные по плагинам."),
            go { busPlugins(it, 0) },
        )
        builder.button(
            28, section(Icons.CLOCK, "CLOCK", "Медленные", "Слушатели, которые дольше всех работают."),
            go {
                val slow = ListenerQuery("Медленные", { who -> busHome(who) }, { l -> l.invocations > 0 }, compareByDescending { l -> l.averageNanos })
                busListeners(it, slow, 0)
            },
        )
        builder.button(
            30,
            icon(
                Icons.PROBLEMS, "BARRIER", "Ошибки слушателей",
                Style.lore(listOf("Слушатели, которые падали."), listOf("Ошибок" to "$failures"), Style.left("открыть")), danger = failures > 0,
            ),
            go { busListeners(it, ListenerQuery("Ошибки", { who -> busHome(who) }, { l -> l.failures > 0 }, compareByDescending { l -> l.failures }), 0) },
        )
        builder.button(
            32, section(Icons.CHANGELOG, "WRITTEN_BOOK", "Записи вызовов", "Подробный разбор записанных вызовов.", "Записывается" to "$traced"),
            go { busTraces(it, null, 0) },
        )
        builder.button(34, section(Icons.HISTORY, "BOOK", "История по дням", "Сколько раз вызывали события каждый день."), go { busDays(it, null, 0) })
        builder.button(
            38,
            icon(
                Icons.BACKUPS, "CHEST", "Выгрузить в файл",
                Style.lore(listOf("Записать счётчики в CSV рядом с историей."), emptyList(), Style.left("выгрузить")),
            ),
            go { busExport(it) },
        )
        builder.button(HOME_CLOSE, backItem(), go { home(it) })
        show(player, builder.build())
    }

    private fun busEvents(player: Player, filter: BusFilter, page: Int) {
        val next = BusFilter.values()[(filter.ordinal + 1) % BusFilter.values().size]
        val rows = bus.events().filter(filter.keep).sortedWith(compareByDescending<EventInfo> { it.lastCalledAt }.thenByDescending { it.calls }).map { info ->
            Row(
                eventItem(info),
                goClick { who, click ->
                    if (click == ClickType.RIGHT) {
                        switchTrace(info.eventType)
                        busEvents(who, filter, page)
                    } else {
                        busEvent(who, info.eventType) { busEvents(it, filter, page) }
                    }
                },
            )
        }
        val cycle = icon(
            Icons.SETTINGS, "HOPPER", "Показать: ${filter.label}",
            Style.lore(
                listOf("Какие события показывать."), emptyList(), Style.left("переключить"),
                extra = Style.choices(BusFilter.values().map { it.label }, filter.ordinal),
            ),
        )
        list(
            player, "События: ${filter.label}", rows, page, PAGE_SIZE, { who, to -> busEvents(who, filter, to) }, { busHome(it) }, "Событий нет",
            extra = { it.button(FILTER, cycle, go { who -> busEvents(who, next, 0) }) },
        )
    }

    private fun eventItem(info: EventInfo): ItemStack {
        val recording = info.eventType in bus.tracedEvents()
        val type = bus.eventType(info.eventType)
        val owner = pluginOf(type)
        val purpose = type?.getAnnotation(EventDescription::class.java)?.purpose?.takeIf { it.isNotBlank() }?.let { wrap(it, limit = 3) }.orEmpty()
        val parameters = buildList {
            add("Плагин" to (owner?.let { "${it.name} ${it.description.version}" } ?: "сервер или библиотека"))
            add("Слушателей" to "${info.listeners}")
            add("Вызовов" to "${info.calls}")
            info.lastCalledAt?.let { add("Последний" to "${stamp(it)}, ${ago(it)}") }
            if (info.denied > 0) add("Отказов" to "${info.denied}")
            add("Запись" to if (recording) "● идёт" else "не ведётся")
        }
        val material = if (info.listeners > 0) "REDSTONE_TORCH" else "COAL"
        val lore = Style.lore(purpose, parameters, Style.left("открыть событие"), Style.right(if (recording) "остановить запись" else "начать запись"))
        return icon(null, material, shortName(info.eventType), lore, alternative = "REDSTONE_TORCH_ON".takeIf { info.listeners > 0 }, warn = info.denied > 0)
    }

    /** One event: its counters, the recording switch and the ways to its listeners, days and recorded calls. */
    private fun busEvent(player: Player, name: String, back: (Player) -> Unit) {
        val info = bus.events().firstOrNull { it.eventType == name }
        if (info == null) {
            tell(player, ChatTone.INFO, "Такого события больше нет.")
            return back(player)
        }
        val recording = name in bus.tracedEvents()
        val type = bus.eventType(name)
        val about = type?.getAnnotation(EventDescription::class.java)
        val builder = frame(Menus.chest(title(shortName(name))).rows(DETAIL_ROWS), DETAIL_ROWS)
        val parameters = buildList {
            addAll(ownerParameters(pluginOf(type)))
            add("Слушателей" to "${info.listeners}")
            add("Вызовов" to "${info.calls}")
            add("Отказов" to "${info.denied}")
            add("Последний вызов" to (info.lastCalledAt?.let { "${stamp(it)}, ${ago(it)}" } ?: "с запуска не было"))
            val days = bus.history(name).map { it.date }
            val today = LocalDate.now(DisplayZone.zone)
            if (days.isNotEmpty()) {
                add("Первый день" to dayTitle(days.min(), today))
                add("Последний день" to dayTitle(days.max(), today))
            }
            addAll(labelled("Класс", name))
            about?.usage?.takeIf { it.isNotBlank() }?.let { addAll(labelled("Как использовать", it)) }
        }
        val purpose = about?.purpose?.takeIf { it.isNotBlank() }?.let { wrap(it) } ?: listOf("Автор плагина не описал это событие.")
        builder.slot(4, icon(null, "NETHER_STAR", shortName(name), Style.lore(purpose, parameters)))
        val record = Style.lore(
            listOf("Запоминать каждый вызов: кто ответил и что изменил."),
            listOf("Сейчас" to if (recording) "● запись идёт" else "не ведётся"),
            Style.left(if (recording) "остановить" else "начать"),
        )
        builder.button(
            20,
            icon(Icons.TOGGLE, "LEVER", "Запись вызовов", record, good = recording),
            go { who -> switchTrace(name); busEvent(who, name, back) },
        )
        builder.button(
            22,
            section(Icons.PLUGINS, "COMPARATOR", "Слушатели", "Кто слушает это событие, по порядку вызова.", "Всего" to "${info.listeners}"),
            go { who -> busListeners(who, ListenerQuery("Слушатели ${shortName(name)}", { busEvent(it, name, back) }, { l -> l.eventType == name }), 0) },
        )
        builder.button(24, section(Icons.HISTORY, "BOOK", "История", "Вызовы этого события по дням."), go { busDays(it, name, 0) })
        builder.button(
            30, section(Icons.CHANGELOG, "WRITTEN_BOOK", "Записанные вызовы", "Что случалось при каждом вызове."),
            go { busTraces(it, name, 0) },
        )
        builder.button(DETAIL_BACK, backItem(), go { back(it) })
        show(player, builder.build())
    }

    private fun switchTrace(name: String) {
        val type = bus.eventType(name) ?: return
        if (name in bus.tracedEvents()) bus.disableTracing(type) else bus.enableTracing(type)
    }

    /** What a list of listeners shows: its [title], which listeners to keep, their order and where to go back. */
    private class ListenerQuery(
        val title: String,
        val back: (Player) -> Unit,
        val keep: (ListenerInfo) -> Boolean = { true },
        val order: Comparator<ListenerInfo>? = null,
    )

    private fun busListeners(player: Player, query: ListenerQuery, page: Int) {
        val all = bus.listeners().filter(query.keep).let { if (query.order != null) it.sortedWith(query.order) else it }
        val rows = all.map { info ->
            Row(listenerItem(info), go { who -> busEvent(who, info.eventType) { busListeners(it, query, page) } })
        }
        list(player, query.title, rows, page, PAGE_SIZE, { who, to -> busListeners(who, query, to) }, query.back, "Слушателей нет")
    }

    private fun listenerItem(info: ListenerInfo): ItemStack {
        val owner = info.module?.let { "${info.plugin}/$it" } ?: info.plugin.toString()
        val parameters = buildList {
            add("Плагин" to ownerLine(info.plugin.toString()))
            add("Событие" to shortName(info.eventType))
            add("Приоритет" to "${info.priority}")
            add("Запусков" to "${info.invocations}")
            add("Среднее" to duration(info.averageNanos))
            add("Максимум" to duration(info.maxNanos))
            if (info.failures > 0) add("Ошибок" to "✖ ${info.failures}")
            if (info.ignoreDenied) add("Особенность" to "работает даже после отказа")
            add("Откуда" to info.source.take(MESSAGE_PREVIEW))
        }
        val lore = Style.lore(emptyList(), parameters, Style.left("открыть событие"))
        return icon(null, "COMPARATOR", owner, lore, alternative = "REDSTONE_COMPARATOR", danger = info.failures > 0)
    }

    private fun busPlugins(player: Player, page: Int) {
        val rows = bus.listeners().groupBy { it.plugin.toString() }.entries.sortedByDescending { it.value.size }.map { (id, group) ->
            val failures = group.sumOf { it.failures }
            val parameters = buildList {
                add("Слушателей" to "${group.size}")
                add("Событий" to "${group.map { it.eventType }.distinct().size}")
                add("Запусков" to "${group.sumOf { it.invocations }}")
                add("Время всего" to duration(group.sumOf { it.totalNanos }))
                if (failures > 0) add("Ошибок" to "✖ $failures")
            }
            val query = ListenerQuery(
                "Плагин $id", { who -> busPlugins(who, page) }, { l -> l.plugin.toString() == id }, compareByDescending { l -> l.totalNanos },
            )
            Row(
                icon(null, "BOOKSHELF", id, Style.lore(emptyList(), parameters, Style.left("слушатели плагина")), danger = failures > 0),
                go { busListeners(it, query, 0) },
            )
        }
        list(player, "События по плагинам", rows, page, PAGE_SIZE, { who, to -> busPlugins(who, to) }, { busHome(it) }, "Слушателей нет")
    }

    /** One row per day, newest first; for one [event] a click opens the event, otherwise the day. */
    private fun busDays(player: Player, event: String?, page: Int) {
        val back: (Player) -> Unit = if (event == null) { who -> busHome(who) } else { who -> busEvent(who, event) { busHome(it) } }
        val today = LocalDate.now(DisplayZone.zone)
        val days = bus.history(event).groupBy { it.date }.entries.sortedByDescending { it.key }
        val busiest = days.maxOfOrNull { (_, entries) -> entries.sumOf { it.calls } } ?: 1L
        val rows = days.map { (date, entries) ->
            val total = entries.sumOf { it.calls }
            val denied = entries.sumOf { it.denied }
            val parameters = buildList {
                add("Вызовов" to "$total")
                if (event == null) add("Событий" to "${entries.size}")
                if (denied > 0) add("Отказов" to "$denied")
                add("Нагрузка" to bar(total, busiest))
            }
            val hint = if (event == null) Style.left("события этого дня") else Style.left("открыть событие")
            val click = if (event == null) go { who -> busDay(who, date, 0, page) } else go { who -> busEvent(who, event) { busDays(it, event, page) } }
            Row(icon(null, "PAPER", dayTitle(date, today), Style.lore(emptyList(), parameters, hint)), click)
        }
        val title = if (event == null) "История событий" else "История ${shortName(event)}"
        val empty = if (bus.historyDays <= 0) "История выключена в коде" else "Вызовов за это время не было"
        list(player, title, rows, page, PAGE_SIZE, { who, to -> busDays(who, event, to) }, back, empty)
    }

    private fun busDay(player: Player, date: LocalDate, page: Int, daysPage: Int) {
        val entries = bus.history().filter { it.date == date }.sortedByDescending { it.calls }
        val busiest = entries.firstOrNull()?.calls ?: 1L
        val rows = entries.map { day ->
            val parameters = buildList {
                add("Вызовов" to "${day.calls}")
                if (day.denied > 0) add("Отказов" to "${day.denied}")
                add("Нагрузка" to bar(day.calls, busiest))
            }
            Row(
                icon(null, "PAPER", shortName(day.eventType), Style.lore(emptyList(), parameters, Style.left("открыть событие")), warn = day.denied > 0),
                go { who -> busEvent(who, day.eventType) { busDay(it, date, page, daysPage) } },
            )
        }
        val today = LocalDate.now(DisplayZone.zone)
        list(
            player, dayTitle(date, today), rows, page, PAGE_SIZE, { who, to -> busDay(who, date, to, daysPage) },
            { busDays(it, null, daysPage) }, "В этот день вызовов не было",
        )
    }

    /** Recorded calls, newest first, each with the answer of every listener; clearing sits in the footer. */
    private fun busTraces(player: Player, event: String?, page: Int) {
        val all = bus.traces().filter { event == null || it.eventName == event }.reversed()
        val rows = all.map { trace -> Row(traceItem(trace), go { who -> busTrace(who, trace, 0) { busTraces(it, event, page) } }) }
        val back: (Player) -> Unit = if (event == null) { who -> busHome(who) } else { who -> busEvent(who, event) { busHome(it) } }
        val clear = icon(
            Icons.RESET, "TNT", "Очистить записи",
            Style.lore(listOf("Забыть все записанные вызовы."), emptyList(), Style.left("очистить")), danger = true,
        )
        list(
            player, "Записанные вызовы", rows, page, PAGE_SIZE, { who, to -> busTraces(who, event, to) }, back,
            "Пока ничего не записано. Включите запись на странице события.",
            extra = { if (all.isNotEmpty()) it.button(FILTER, clear, go { who -> bus.clearTraces(); busTraces(who, event, 0) }) },
        )
    }

    private fun traceItem(trace: EventTrace): ItemStack {
        val steps = trace.steps.take(TRACE_STEPS).map { step ->
            val owner = step.module?.let { "${step.plugin}/$it" } ?: step.plugin.toString()
            val action = when {
                step.skipped -> "пропущен"
                step.failed -> "упал"
                step.changedResult -> "${step.resultBefore} → ${step.resultAfter}"
                else -> "без изменений"
            }
            "" to "$owner · $action"
        }
        val parameters = buildList {
            trace.at?.let { add("Когда" to stamp(it)); add("" to ago(it)) }
            add("Результат" to (trace.result ?: "-"))
            add("Режим" to if (trace.mode == DispatchMode.ASYNC) "async, мир не трогать" else "sync")
            add("Поток" to trace.thread)
            add("Ответили" to "${trace.steps.size}")
            addAll(steps)
        }
        val verdict = if (trace.allowed) "разрешено" else "отклонено"
        val lore = Style.lore(emptyList(), parameters, Style.left("все ответы"))
        return icon(null, "PAPER", "${shortName(trace.eventName)}: $verdict", lore, danger = !trace.allowed)
    }

    /** One recorded call with every listener that answered, in the order they were asked. */
    private fun busTrace(player: Player, trace: EventTrace, page: Int, back: (Player) -> Unit) {
        val rows = trace.steps.mapIndexed { index, step ->
            val owner = step.module?.let { "${step.plugin}/$it" } ?: step.plugin.toString()
            val action = when {
                step.skipped -> "пропущен"
                step.failed -> "упал"
                step.changedResult -> "изменил результат"
                else -> "без изменений"
            }
            val parameters = buildList {
                add("Плагин" to ownerLine(step.plugin.toString()))
                add("Порядок" to "${index + 1} из ${trace.steps.size}")
                add("Приоритет" to "${step.priority}")
                add("Что сделал" to action)
                if (step.changedResult) add("Результат" to "${step.resultBefore} → ${step.resultAfter}")
                if (!step.skipped) add("Время" to duration(step.nanos))
            }
            Row(icon(null, "PAPER", owner, Style.lore(emptyList(), parameters), danger = step.failed, warn = step.changedResult, muted = step.skipped))
        }
        val title = "${shortName(trace.eventName)}${trace.at?.let { ", ${stamp(it)}" }.orEmpty()}"
        list(player, title.take(TITLE_LIMIT), rows, page, PAGE_SIZE, { who, to -> busTrace(who, trace, to, back) }, back, "Никто не отвечал")
    }

    private fun busExport(player: Player) {
        val file = bus.exportHistory(false)
        if (file == null) tell(player, ChatTone.FAILURE, "Выгрузка недоступна.", "История выключена или файл не записать.")
        else tell(player, ChatTone.SUCCESS, "Счётчики записаны.", file.toString())
    }

    private fun duration(nanos: Long): String =
        if (nanos >= NANOS_PER_MILLI) "%.1f мс".format(nanos / NANOS_PER_MILLI.toDouble()) else "%.1f мкс".format(nanos / NANOS_PER_MICRO.toDouble())

    private fun shortName(name: String): String = name.substringAfterLast('.')

    /** A moment in the configured time zone, to the minute: `29 октября 2026, 14:05`; the year is left out for this year. */
    private fun time(moment: Instant): String = format(moment, withSeconds = false)

    /** A moment in the configured time zone, to the second: `29 октября 2026, 14:05:09`. */
    private fun stamp(moment: Instant): String = format(moment, withSeconds = true)

    private fun format(moment: Instant, withSeconds: Boolean): String {
        val zone = DisplayZone.zone
        val year = if (moment.atZone(zone).year == LocalDate.now(zone).year) "" else " yyyy"
        val clock = if (withSeconds) "HH:mm:ss" else "HH:mm"
        return DateTimeFormatter.ofPattern("d MMMM$year, $clock", Locale("ru")).withZone(zone).format(moment)
    }

    /** How long ago [moment] was, or how long until it: `только что`, `5 мин назад`, `через 2 ч 10 мин`. */
    private fun ago(moment: Instant): String {
        val span = Duration.between(moment, Instant.now())
        return when {
            span.abs().seconds < SECONDS_PER_MINUTE -> "только что"
            span.isNegative -> "через ${left(span.negated())}"
            else -> "${left(span)} назад"
        }
    }

    private fun dayTitle(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Сегодня"
        today.minusDays(1) -> "Вчера"
        else -> (if (date.year == today.year) DAY else DAY_YEAR).format(date)
    }

    private fun bar(value: Long, busiest: Long): String {
        val width = ((value * BAR_WIDTH + busiest - 1) / busiest.coerceAtLeast(1)).toInt().coerceIn(1, BAR_WIDTH)
        return "█".repeat(width)
    }

    /** [text] cut into lines of at most [width] letters at word borders, at most [limit] lines, ending with "…" when cut. */
    private fun wrap(text: String, width: Int = WRAP_WIDTH, limit: Int = WRAP_LINES): List<String> {
        val lines = ArrayList<String>()
        for (raw in text.lines()) {
            var line = StringBuilder()
            for (word in raw.trim().split(' ')) {
                if (line.isNotEmpty() && line.length + 1 + word.length > width) {
                    lines += line.toString()
                    line = StringBuilder()
                }
                var rest = word
                while (rest.length > width) {
                    lines += rest.take(width)
                    rest = rest.drop(width)
                }
                if (line.isNotEmpty()) line.append(' ')
                line.append(rest)
            }
            lines += line.toString()
        }
        return if (lines.size > limit) lines.take(limit) + "…" else lines
    }

    private fun journalMaterial(level: String): String = when (level) {
        "error" -> "BARRIER"
        "warning" -> "GOLD_INGOT"
        else -> "PAPER"
    }

    // --- actions -------------------------------------------------------------------------------------------------

    private fun checkAll(player: Player) {
        player.closeInventory()
        tell(player, ChatTone.PROGRESS, "Проверяю обновления…")
        library.updates.checkNow(replies.updateActor(player)).whenComplete { _, error ->
            replies.onServerThread {
                if (!player.isOnline) return@onServerThread
                if (error != null) return@onServerThread tell(player, ChatTone.FAILURE, "Не удалось проверить обновления.", reason(error))
                val snapshots = snapshots()
                val news = UpdateCatalogView.announcement(snapshots, BukkitControlCommand.ROUTES, PnLibraryIdentity.SUPPORT_URL)
                if (news.isEmpty()) tell(player, ChatTone.SUCCESS, "Проверка завершена: все плагины используют актуальные версии.")
                else audience(player)?.let { news.forEach(it::sendMessage) }
            }
        }
    }

    private fun checkOne(player: Player, product: String) {
        player.closeInventory()
        tell(player, ChatTone.PROGRESS, "Проверяю обновления $product…")
        library.updates.get(product)?.checkNow()
        plugin.server.scheduler.runTaskLater(plugin, Runnable { if (player.isOnline) card(player, product) }, CHECK_REOPEN_TICKS)
    }

    private fun toggleAuto(player: Player, snapshot: UpdateSnapshot) {
        val enable = !snapshot.automaticDownload
        library.updates.setAutomaticDownload(snapshot.product, enable, replies.updateActor(player)).whenComplete { _, error ->
            replies.onServerThread {
                if (error != null) tell(player, ChatTone.FAILURE, "Не удалось изменить автозагрузку.", reason(error))
                else tell(player, ChatTone.SUCCESS, "Автозагрузка ${snapshot.product}: ${if (enable) "включена" else "выключена"}.")
                card(player, snapshot.product)
            }
        }
    }

    private fun chooseChannel(player: Player, product: String, channel: UpdateChannel?) {
        library.updates.setChannel(product, channel, replies.updateActor(player)).whenComplete { _, error ->
            replies.onServerThread {
                if (error != null) tell(player, ChatTone.FAILURE, "Не удалось изменить канал.", reason(error))
                else tell(player, ChatTone.SUCCESS, "Канал $product: ${channel?.name?.lowercase() ?: "как указал плагин"}.")
                card(player, product)
            }
        }
    }

    private fun chooseHold(player: Player, product: String, duration: Duration?) {
        library.updates.setHeld(product, duration, replies.updateActor(player)).whenComplete { _, error ->
            replies.onServerThread {
                if (error != null) tell(player, ChatTone.FAILURE, "Не удалось изменить паузу.", reason(error))
                else tell(player, ChatTone.SUCCESS, if (duration == null) "Пауза $product снята." else "Обновления $product приостановлены.")
                card(player, product)
            }
        }
    }

    /** Runs [change] for every registered plugin, then reports [done] and reopens the settings. */
    private fun everyone(player: Player, done: String, change: (UpdateSnapshot) -> java.util.concurrent.CompletionStage<*>) {
        val stages = snapshots().map { change(it).toCompletableFuture() }
        java.util.concurrent.CompletableFuture.allOf(*stages.toTypedArray()).whenComplete { _, error ->
            replies.onServerThread {
                if (error != null) tell(player, ChatTone.FAILURE, "Не всё удалось изменить.", reason(error))
                else tell(player, ChatTone.SUCCESS, done)
                settings(player)
            }
        }
    }

    private fun setAutomatic(snapshot: UpdateSnapshot, enable: Boolean, player: Player) =
        library.updates.setAutomaticDownload(snapshot.product, enable, replies.updateActor(player))

    private fun hold(snapshot: UpdateSnapshot, duration: Duration?, player: Player) =
        library.updates.setHeld(snapshot.product, duration, replies.updateActor(player))

    private fun rollback(player: Player, backup: UpdateBackup, restoreConfigurations: Boolean) {
        player.closeInventory()
        library.updates.rollback(backup.id, restoreConfigurations, replies.updateActor(player)).whenComplete { _, error ->
            replies.onServerThread {
                if (!player.isOnline) return@onServerThread
                if (error != null) tell(player, ChatTone.FAILURE, "Не удалось подготовить откат.", reason(error))
                else tell(player, ChatTone.SUCCESS, "Резервный набор подготовлен.", "Полностью перезапустите сервер, чтобы применить его.")
            }
        }
    }

    private fun support(player: Player) {
        player.closeInventory()
        val card = UpdateCatalogView.support(PnLibraryIdentity.SUPPORT_URL, BukkitControlCommand.ROUTES)
        audience(player)?.let { card.forEach(it::sendMessage) }
    }

    /** Runs [action] when [player] holds [permission], otherwise says what is missing. */
    private fun allowed(player: Player, permission: String, action: () -> Unit) {
        if (player.hasPermission(permission) || player.hasPermission("pnlibrary.admin")) action()
        else tell(player, ChatTone.FAILURE, "Не хватает права.", permission)
    }

    private fun audience(player: Player) = library.audiences.player(player.uniqueId)

    /** Sends one notice in the style of the chat commands. */
    private fun tell(player: Player, tone: ChatTone, text: String, detail: String? = null) {
        val lines = UpdateCatalogView.notice(ChatNotice(tone, text, detail), BukkitControlCommand.ROUTES)
        audience(player)?.let { lines.forEach(it::sendMessage) }
    }

    private fun reason(error: Throwable): String {
        val cause = generateSequence(error) { it.cause }.last()
        return cause.message ?: cause.javaClass.simpleName
    }

    // --- building blocks -----------------------------------------------------------------------------------------

    /**
     * A page of a list. [rows] are the rows of [page] when [total] says the list is larger than [rows], otherwise
     * the whole list that is cut into pages here. [reopen] receives the player and the page to open.
     */
    private fun list(
        player: Player,
        title: String,
        rows: List<Row>,
        page: Int,
        perPage: Int,
        reopen: (Player, Int) -> Unit,
        back: (Player) -> Unit,
        emptyText: String,
        total: Int = rows.size,
        extra: (MenuBuilder) -> Unit = {},
    ) {
        val pages = ((total - 1) / perPage + 1).coerceAtLeast(1)
        val current = page.coerceIn(0, pages - 1)
        val shown = if (total == rows.size) rows.drop(current * perPage).take(perPage) else rows.take(perPage)
        val builder = Menus.chest(title("$title ${current + 1}/$pages")).rows(LIST_ROWS)
        shown.forEachIndexed { slot, row -> if (row.click != null) builder.button(slot, row.stack, row.click) else builder.slot(slot, row.stack) }
        if (total == 0) builder.slot(22, icon(Icons.PROBLEMS, "BARRIER", emptyText, emptyList(), muted = true))
        footerCorners(builder)
        builder.fill(pane(), 0, FOOTER_END)
        builder.button(BACK, backItem(), go { back(it) })
        if (current > 0) {
            val previous = icon(Icons.BACK, "ARROW", "Назад по списку", Style.lore(emptyList(), emptyList(), Style.left("страница $current")), good = true)
            builder.button(PREVIOUS, previous, go { reopen(it, current - 1) })
        }
        if (current < pages - 1) {
            val following = icon(Icons.NEXT, "ARROW", "Дальше по списку",
                Style.lore(emptyList(), emptyList(), Style.left("страница ${current + 2}")), good = true)
            builder.button(NEXT, following, go { reopen(it, current + 1) })
        }
        builder.button(
            REFRESH,
            icon(Icons.CHECK, "COMPASS", "Обновить список", Style.lore(listOf("Перечитать данные заново."), emptyList(), Style.left("обновить"))),
            go { reopen(it, current) },
        )
        extra(builder)
        builder.button(CLOSE, closeItem(), go { it.closeInventory() })
        show(player, builder.build())
    }

    private fun go(action: (Player) -> Unit) = MenuClickHandler { action(it.player) }

    /** A handler that also learns which click was used: left, right or shift-left. */
    private fun goClick(action: (Player, ClickType) -> Unit) = MenuClickHandler { action(it.player, it.click) }

    private fun show(player: Player, menu: Menu) {
        library.platform<BukkitPlatform>().menus.open(plugin, player, menu)
        OPEN_SOUND?.let { player.playSound(player.location, it, SOUND_VOLUME, SOUND_PITCH) }
    }

    private fun snapshots(): List<UpdateSnapshot> = library.updates.all().map { it.snapshot }.sortedBy { it.product.lowercase() }

    private fun connectedCount(): Int {
        val names = snapshots().map { it.product.lowercase() }.toSet()
        return names.size + library.plugins.all().count { nameOf(it.owner).lowercase() !in names }
    }

    private fun nameOf(owner: Any): String = (owner as? Plugin)?.name ?: owner.javaClass.simpleName

    /** Plugins connected to the library that have no update registration: they are listed so none is missing. */
    private fun unmanaged(known: Set<String>): List<Row> = library.plugins.all()
        .filter { nameOf(it.owner).lowercase() !in known }
        .sortedBy { nameOf(it.owner).lowercase() }
        .map { registration ->
            val owner = registration.owner as? Plugin
            val parameters = buildList {
                owner?.description?.version?.let { add("Версия" to it) }
                add("Модулей" to "${registration.all().size}")
                add("Обновления" to "плагин не подключён к системе обновлений")
            }
            Row(icon(Icons.PLUGIN, "PAPER", nameOf(registration.owner), Style.lore(emptyList(), parameters)))
        }

    private fun plate(snapshot: UpdateSnapshot, withHint: Boolean = true, hints: Boolean = false): ItemStack {
        val material = when (snapshot.state) {
            UpdateState.UP_TO_DATE -> "EMERALD"
            UpdateState.UPDATE_AVAILABLE -> "GOLD_INGOT"
            UpdateState.UPDATE_STAGED, UpdateState.DOWNLOADING, UpdateState.ROLLED_BACK -> "DIAMOND"
            UpdateState.FROZEN -> "CLOCK"
            UpdateState.CHECKING -> "COMPASS"
            UpdateState.FAILED, UpdateState.BLOCKED, UpdateState.INCOMPATIBLE -> "BARRIER"
        }
        val problem = snapshot.state in CatalogLabels.PROBLEMS
        val blockers = if (snapshot.state == UpdateState.BLOCKED) {
            library.updates.currentPlan().orElse(null)?.blockers.orEmpty().take(BLOCKER_LINES).map { BukkitUpdateMessages.blocker(it) }
        } else {
            emptyList()
        }
        val parameters = buildList {
            add("Версия" to snapshot.currentVersion)
            snapshot.latestVersion?.let { add("Последняя" to it) }
            add("Канал" to snapshot.channel.name.lowercase())
            add("Состояние" to "${stateSymbol(snapshot.state)} ${CatalogLabels.stateLabel(snapshot.state)}")
            add("Автозагрузка" to if (snapshot.automaticDownload) "✔ включена" else "✖ выключена")
            snapshot.supportedApi?.let { add("API pnLibrary" to "${it.minimum}–${it.maximum}") }
        }
        val problems = (listOfNotNull(snapshot.message.takeIf { problem }) + blockers).map { Style.danger(it) }
        val clicks = when {
            hints -> arrayOf(Style.left("открыть карточку"), Style.right("проверить"), Style.shiftLeft("переключить автозагрузку"))
            withHint -> arrayOf(Style.left("открыть карточку"))
            else -> emptyArray()
        }
        val lore = Style.lore(emptyList(), parameters, *clicks, extra = problems)
        return icon(null, material, snapshot.product, lore, alternative = "WATCH".takeIf { material == "CLOCK" }, danger = problem)
    }

    private fun stateSymbol(state: UpdateState): String = when (state) {
        UpdateState.UP_TO_DATE -> "✔"
        UpdateState.UPDATE_AVAILABLE -> "✦"
        UpdateState.UPDATE_STAGED, UpdateState.DOWNLOADING, UpdateState.ROLLED_BACK -> "➜"
        UpdateState.FROZEN -> "‖"
        UpdateState.CHECKING -> "…"
        UpdateState.FAILED, UpdateState.BLOCKED, UpdateState.INCOMPATIBLE -> "✖"
    }

    private fun autoItem(snapshot: UpdateSnapshot): ItemStack = icon(
        Icons.TOGGLE, "REDSTONE_TORCH", "Автозагрузка",
        Style.lore(
            listOf("Готовить новые версии автоматически."),
            listOf("Сейчас" to if (snapshot.automaticDownload) "включена" else "выключена"),
            Style.left("переключить"),
        ),
        alternative = "REDSTONE_TORCH_ON", good = snapshot.automaticDownload,
    )

    private fun holdItem(snapshot: UpdateSnapshot): ItemStack {
        val held = snapshot.state == UpdateState.FROZEN
        val lore = Style.lore(
            listOf(if (held) "Обновления сейчас приостановлены." else "Приостановить обновления на время."),
            emptyList(), Style.left(if (held) "изменить или снять" else "выбрать срок"),
        )
        return icon(Icons.CLOCK, "CLOCK", "Пауза", lore, alternative = "WATCH", warn = held)
    }

    private fun historyItem(entry: UpdateHistoryEntry, full: Boolean = false): ItemStack {
        val failed = entry.state == UpdateState.FAILED
        val parameters = buildList {
            add("Когда" to time(entry.occurredAt))
            add("Кто" to entry.actor.name)
            entry.changes.forEach { add(it.product.value to "${it.from} » ${it.to}") }
        }
        val note = entry.message?.let { wrap(it, limit = if (full) NOTE_LINES else 2) }.orEmpty()
            .map { if (failed) Style.danger(it) else Style.description(it) }
        val title = "${CatalogLabels.operationLabel(entry.operation)}: ${CatalogLabels.stateLabel(entry.state)}"
        val hints = if (full) emptyArray() else arrayOf(Style.left("подробности"))
        return icon(null, if (failed) "BARRIER" else "PAPER", title, Style.lore(emptyList(), parameters, *hints, extra = note), danger = failed)
    }

    private fun backupItem(backup: UpdateBackup, withHint: Boolean = true): ItemStack {
        val parameters = buildList {
            backup.createdAt?.let { add("Создана" to time(it)) }
            backup.components.forEach { add(it.product.value to "${it.backupVersion ?: "?"} (было ${it.updatedVersion ?: "?"})") }
            if (backup.configurationFiles > 0) add("Файлов настроек" to "${backup.configurationFiles}")
        }
        val lore = Style.lore(emptyList(), parameters, *if (withHint) arrayOf(Style.left("выбрать откат")) else emptyArray())
        return icon(Icons.BACKUPS, "CHEST", "Резервный набор", lore)
    }

    private fun section(texture: String?, material: String, label: String, description: String, vararg parameters: Pair<String, String>): ItemStack =
        icon(texture, material, label, Style.lore(listOf(description), parameters.toList(), Style.left("открыть")))

    private fun backItem(label: String = "Назад"): ItemStack =
        icon(Icons.BACK, "ARROW", "« $label", Style.lore(emptyList(), emptyList(), Style.left("вернуться")), warn = true)

    private fun closeItem(): ItemStack =
        icon(Icons.BACK, "BARRIER", "Закрыть", Style.lore(emptyList(), emptyList(), Style.left("закрыть меню")), danger = true)

    private fun title(text: String): String = "§0pnLibrary §5» §0$text"

    /** Violet corner brackets on the four corners of a [rows]-row menu, then the dark glass between them. */
    private fun frame(builder: MenuBuilder, rows: Int): MenuBuilder {
        val last = (rows - 1) * 9
        listOf(0, 1, 9, 7, 8, 17, last, last + 1, last - 9, last + 7, last + 8, last - 1).forEach { builder.slot(it, accent()) }
        return builder.fill(pane())
    }

    /** The two bottom corners of a list, leaving the rows above for the entries. */
    private fun footerCorners(builder: MenuBuilder) {
        listOf(FOOTER_START, FOOTER_START + 1, FOOTER_END - 2, FOOTER_END - 1).forEach { builder.slot(it, accent()) }
    }

    private fun pane(): ItemStack = glass("BLACK_STAINED_GLASS_PANE", LEGACY_BLACK)

    private fun accent(): ItemStack = glass("PURPLE_STAINED_GLASS_PANE", LEGACY_PURPLE)

    private fun glass(modern: String, legacyData: Short): ItemStack {
        val stack = item(modern, " ", emptyList(), "STAINED_GLASS_PANE")
        if (stack.type.name in LEGACY_GLASS) stack.durability = legacyData
        return stack
    }

    /**
     * A button: the textured head [texture] when the server can make one, otherwise the item [material]
     * (or [alternative] on old servers). The name colour says what the button is for.
     */
    private fun icon(
        texture: String?,
        material: String,
        label: String,
        lore: List<String>,
        alternative: String? = null,
        good: Boolean = false,
        warn: Boolean = false,
        danger: Boolean = false,
        muted: Boolean = false,
    ): ItemStack {
        val tone = when {
            danger -> Tone.DANGER
            warn -> Tone.WARN
            good -> Tone.GOOD
            muted -> Tone.MUTED
            else -> Tone.NORMAL
        }
        val name = Style.name(label, tone)
        val head = texture?.let { MenuHeads.head(it) }
        return decorate(head ?: item(material, name, emptyList(), alternative), name, lore)
    }

    private fun item(material: String, name: String, lore: List<String> = emptyList(), alternative: String? = null): ItemStack {
        val type = listOfNotNull(material, alternative).firstNotNullOfOrNull { resolve(it) } ?: Material.PAPER
        return decorate(ItemStack(type), name, lore)
    }

    /** Looks a material up by its current name, then by the name the server keeps for older ones. */
    private fun resolve(name: String): Material? =
        runCatching { Material.getMaterial(name) }.getOrNull()
            ?: Material.matchMaterial(name)
            ?: runCatching { Material.getMaterial("LEGACY_$name") }.getOrNull()

    private fun decorate(stack: ItemStack, name: String, lore: List<String>): ItemStack = stack.also {
        it.itemMeta?.let { meta ->
            meta.setDisplayName(name)
            meta.lore = lore
            ItemFlag.values().forEach { flag -> meta.addItemFlags(flag) }
            it.itemMeta = meta
        }
    }

    /** Colours and lines of the menu: the library's violet and pink, a gradient on every name and click hint. */
    private object Style {
        private val DIM = MenuHeads.color("&#64546E")
        private val PLAIN = MenuHeads.color("&#F7F2F7")
        private val DESCRIPTION = MenuHeads.color(" &#E9DDF2")
        private val SUB = MenuHeads.color("   &#8B7A99↳ &#F7F2F7")
        private val TAGS = listOf("D19BFF", "5BC8F5", "6EE7A0", "FFB347", "FF9BD2", "FDE047", "B79CFF")

        fun name(label: String, tone: Tone): String = MenuHeads.gradient(label, tone.from, tone.to)

        private fun click(label: String, from: String, to: String, action: String): String =
            "$DIM ▸ " + MenuHeads.gradient(label, from, to) + "  $PLAIN" + action

        fun left(action: String): String = click("Клик", "D19BFF", "F5C6FF", action)

        fun right(action: String): String = click("Правый клик", "FF9BD2", "FF7A9B", action)

        fun shiftLeft(action: String): String = click("Shift + клик", "5BC8F5", "B5ECFF", action)

        fun description(text: String): String = DESCRIPTION + text

        /** Every choice of a switch on its own line; the chosen one has a coloured mark and a gradient, the others are dim. */
        fun choices(options: List<String>, selected: Int): List<String> = options.mapIndexed { index, label ->
            if (index == selected) {
                MenuHeads.color("   &#6EE7A0➤ ") + MenuHeads.gradient(label, "D19BFF", "FF9BD2")
            } else {
                MenuHeads.color("   &#64546E· &#8B7A99") + label
            }
        }

        fun danger(text: String): String = MenuHeads.color(" &#FF7A7A") + text

        /** A bracketed label such as `[ Версия ]`, in a colour that stays the same for the same word. */
        fun tag(label: String): String = "$DIM [ " + MenuHeads.color("&#${TAGS[Math.floorMod(label.hashCode(), TAGS.size)]}") + label + " $DIM] $PLAIN"

        /**
         * Description lines, a blank line, `[ label ] value` lines (an empty label continues the line above with an
         * indented `↳ value`), a blank line, then the click hints; framed by blank lines.
         */
        fun lore(
            lines: List<String>,
            parameters: List<Pair<String, String>>,
            vararg hints: String,
            extra: List<String> = emptyList(),
        ): List<String> = buildList {
            add("")
            lines.forEach { add(description(it)) }
            if (lines.isNotEmpty() && parameters.isNotEmpty()) add("")
            parameters.forEach { (label, value) -> add(if (label.isEmpty()) SUB + value else tag(label) + value) }
            if (extra.isNotEmpty()) {
                add("")
                addAll(extra)
            }
            if (hints.isNotEmpty()) {
                add("")
                addAll(hints)
            }
            add("")
        }
    }

    /** Texture hashes of the heads on textures.minecraft.net. */
    private object Icons {
        const val OVERVIEW = "35747010d84aa56483b75f6243d94f34c534603584b2cc68a45bf365840105fd"
        const val PLUGINS = "c16808126eec0cd41d6040e211985719272c388d32ecae795d47f7a9cee71113"
        const val UPDATES = "c2be2091a46355cd30e537e428174d316401e5fbbda53ac3a7e8d7426ec6a814"
        const val PROBLEMS = "375b36c3945fdaa0c740818531a8f45bacd2696ce2ab85a9ea45290accf180cc"
        const val HISTORY = "505f7080eb7ad15a68ae161322cd2ba25da5d24cda452d0e88c8e496ebc6b7a0"
        const val BACKUPS = "5318ee5278e08d9e6fe9163ec4267c791f5248a357f5f780d6346812c704eb8f"
        const val CHECK = "45556083c9cde19f54bf78a421cb9731f60f1d3de3cf584f54b2d43677df2a7"
        const val SUPPORT = "16439d2e306b225516aa9a6d007a7e75edd2d5015d113b42f44be62a517e574f"
        const val BACK = "69ea1d86247f4af351ed1866bca6a3040a06c68177c78e42316a1098e60fb7d3"
        const val NEXT = "8271a47104495e357c3e8e80f511a9f102b0700ca9b88e88b795d33ff20105eb"
        const val TOGGLE = "5f9e02768935593321437f2d6709a59a6e5437eb4301631965e9df5359ac87ef"
        const val LOCKED = "27548362a24c0fa8453e4d93e68c5969ddbde57bf6666c0319c1ed1e84d89065"
        const val SETTINGS = "5949a18cb52c293fe7de7ba1014671340ed7ff8e5d705b2d60bf84d53148e04"
        const val TOOLS = "79924bcfd7ec6ea437438b75b72ee41a2b57104dba25ecafa6118e68e1b81c9d"
        const val RELEASES = "c67d5d37cd64ce3fb57377d25d6152aa4ac2e37954248d5d91f88fbd791f4766"
        const val CHANGELOG = "97a530f5c5742bf19e175a4d78ad435ac0f4396d3b5464bd6182ab382aca417d"
        const val RESTART = "87d885b32b0dd2d6b7f1b582a34186f8a5373c46589a273423132b448b803462"
        const val ROLLBACK = "27548362a24c0fa8453e4d93e68c5969ddbde57bf6666c0319c1ed1e84d89065"
        const val CLOCK = "9f84735fc9c760e95eaf10cec4f10edb5f3822a5ff9551eeb5095135d1ffa302"
        const val RESET = "aa8182f994d6426052f86c50b24c016b2ba94a25aff54abf122034193a1358e3"
        const val PLUGIN = "79924bcfd7ec6ea437438b75b72ee41a2b57104dba25ecafa6118e68e1b81c9d"
    }

    private companion object {
        const val DOWNLOAD = "pnlibrary.updates.download"
        const val SETTINGS = "pnlibrary.updates.settings"
        const val ROLLBACK = "pnlibrary.updates.rollback"
        const val HOME_ROWS = 5
        const val HOME_CLOSE = 40
        const val SUPPORT_SLOT = 38
        const val RESTART_SLOT = 41
        const val ROLLBACK_SLOT = 42
        const val CARD_ROWS = 4
        const val CARD_BACK = 31
        const val SMALL_ROWS = 3
        const val SUPPORT_TOOL_SLOT = 16
        const val SMALL_BACK = 22
        const val LIST_ROWS = 6
        const val PAGE_SIZE = 45
        const val HISTORY_PAGE = 10
        const val FOOTER_START = 45
        const val FOOTER_END = 54
        const val BACK = 45
        const val PREVIOUS = 48
        const val NEXT = 50
        const val REFRESH = 49
        const val FILTER = 47
        const val DATE_FILTER = 46
        const val JOURNAL_HOURS = 24
        const val JOURNAL_LIMIT = 200
        const val MESSAGE_PREVIEW = 70
        val LEVEL_CYCLE = listOf<String?>(null, "error", "warning", "info")
        val LEVELS = mapOf("error" to "ошибки", "warning" to "предупреждения", "info" to "сведения")
        const val CLOSE = 53
        const val BUS = "pnlibrary.events.bus"
        const val CYCLE_HOLDS = 3
        const val HOURS_PER_DAY = 24
        const val MINUTES_PER_HOUR = 60
        const val TASKS = "pnlibrary.tasks"
        const val DETAIL_ROWS = 5
        const val DETAIL_BACK = 40
        const val CHANGE_FIRST = 19
        const val CHANGE_BUTTONS = 7
        const val WRAP_WIDTH = 44
        const val WRAP_LINES = 8
        const val TRACE_LINES = 12
        const val NOTE_LINES = 8
        const val TIMES_SHOWN = 10
        const val DETAILS_SHOWN = 8
        const val TRACE_STEPS = 6
        const val BAR_WIDTH = 12
        const val NANOS_PER_MICRO = 1_000L
        const val NANOS_PER_MILLI = 1_000_000L
        val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", Locale("ru"))
        val DAY_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru"))
        const val SECONDS_PER_MINUTE = 60L
        const val TITLE_LIMIT = 30
        const val TIMES_PREVIEW = 5
        const val BLOCKER_LINES = 3
        const val CHECK_REOPEN_TICKS = 60L
        const val SOUND_VOLUME = 0.22f
        const val SOUND_PITCH = 1.15f
        val LEGACY_GLASS = setOf("STAINED_GLASS_PANE", "LEGACY_STAINED_GLASS_PANE")
        const val LEGACY_BLACK: Short = 15
        const val LEGACY_PURPLE: Short = 10

        val OPEN_SOUND: Sound? = listOf("BLOCK_BARREL_OPEN", "BLOCK_CHEST_OPEN", "CHEST_OPEN")
            .firstNotNullOfOrNull { name -> Sound.values().firstOrNull { it.name == name } }

        /** Whether the menu may change the update channel; only `dev` works for now, so the choice is switched off. */
        const val CHANNEL_SWITCH = false

        val CHANNEL_ITEMS = mapOf(
            UpdateChannel.STABLE to "EMERALD",
            UpdateChannel.RC to "GOLD_INGOT",
            UpdateChannel.BETA to "IRON_INGOT",
            UpdateChannel.ALPHA to "REDSTONE",
            UpdateChannel.DEV to "COAL",
        )
        val CHANNEL_HINTS = mapOf(
            UpdateChannel.STABLE to "Только проверенные релизы.",
            UpdateChannel.RC to "Кандидаты в релиз и стабильные.",
            UpdateChannel.BETA to "Бета-версии, кандидаты и стабильные.",
            UpdateChannel.ALPHA to "Ранние версии: возможны ошибки.",
            UpdateChannel.DEV to "Все версии, включая ежедневные сборки.",
        )
        val HOLDS = listOf(
            "1 час" to Duration.ofHours(1),
            "6 часов" to Duration.ofHours(6),
            "1 день" to Duration.ofDays(1),
            "3 дня" to Duration.ofDays(3),
            "7 дней" to Duration.ofDays(7),
        )
    }
}
