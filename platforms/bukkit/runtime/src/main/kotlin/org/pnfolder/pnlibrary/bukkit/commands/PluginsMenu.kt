package org.pnfolder.pnlibrary.bukkit.commands

import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.pnfolder.pnlibrary.api.chat.ChatTone
import org.pnfolder.pnlibrary.api.platform.platform
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity
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
import org.pnfolder.pnlibrary.core.updates.catalog.CatalogLabels
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.bukkit.command.CommandSender as NativeSender
import org.pnfolder.pnlibrary.api.commands.CommandSender as Reply

/**
 * The inventory menu of `/pn gui`: everything the update commands do, on several connected screens.
 *
 * Screens: the home page, the list of plugins (all, with updates, with problems), the card of one plugin, its
 * channel, pause and releases, the history of one plugin or of all, and the saved backups with a rollback choice.
 * Every action calls the same services as the commands and asks for the same permissions, so the audit record is the
 * same. Item materials are looked up by name with older fallbacks, and colours fall back to the nearest legacy code
 * below 1.16, so the menu opens on every server from 1.8.
 */
internal class PluginsMenu(
    private val plugin: Plugin,
    private val library: PnLibrary,
    private val replies: BukkitCommandReplies,
) {
    private enum class Filter(val title: String) { ALL("плагины"), UPDATES("обновления"), PROBLEMS("проблемы") }

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
        val problems = snapshots.count { it.state in CatalogLabels.PROBLEMS }
        val plan = library.updates.currentPlan().orElse(null)
        val builder = frame(Menus.chest(title("Главная")).rows(HOME_ROWS), HOME_ROWS)
        builder.slot(4, item("NETHER_STAR", name("${PnLibraryIdentity.NAME} ${library.version}"), listOf(
            Style.description("Плагинов подключено: ${snapshots.size}"),
            Style.description("Обновлений: $pending, проблем: $problems"),
        )))
        builder.button(10, nav("BOOKSHELF", "Плагины", "Все плагины и их версии.", "BOOK"), go { plugins(it, Filter.ALL, 0) })
        builder.button(11, nav("GOLD_INGOT", "Обновления", "Есть обновление: $pending."), go { plugins(it, Filter.UPDATES, 0) })
        builder.button(12, nav("BARRIER", "Проблемы", "Ошибки и блокировки: $problems."), go { plugins(it, Filter.PROBLEMS, 0) })
        builder.button(13, nav("BOOK", "История", "Проверки, обновления, откаты и ошибки."), go { history(it, null, 0) })
        builder.button(14, nav("CHEST", "Резервные копии", "Откат к сохранённым версиям."), go { backups(it, 0) })
        builder.button(
            15,
            item("COMPASS", name("Проверить всё"), listOf(Style.description("Спросить каталоги релизов сейчас."), "", Style.LEFT + "проверить")),
            go { checkAll(it) },
        )
        if (plan != null && plan.state == UpdateState.UPDATE_STAGED) {
            val restart = item(
                "DIAMOND", name("Перезапуск"),
                listOf(Style.description("Обновления подготовлены."), "", Style.LEFT + "подтвердить в чате"),
            )
            builder.button(16, restart, go { it.closeInventory(); it.performCommand("pn update restart") })
        }
        if (plan?.plan?.changes?.isNotEmpty() == true) {
            val rollback = item(
                "TNT", name("Откатить план", Style.DANGER),
                listOf(Style.description("Откатит все связанные плагины плана."), "", Style.LEFT + "подтвердить в чате"),
            )
            builder.button(21, rollback, go { it.closeInventory(); it.performCommand("pn update rollback") })
        }
        builder.button(23, item("PAPER", name("Поддержка"), listOf(Style.description("Ссылка на помощь придёт в чат."))), go { support(it) })
        builder.button(HOME_CLOSE, closeItem(), go { it.closeInventory() })
        show(player, builder.build())
    }

    private fun plugins(player: Player, filter: Filter, page: Int) {
        val rows = snapshots().filter {
            when (filter) {
                Filter.ALL -> true
                Filter.UPDATES -> it.state in CatalogLabels.PENDING
                Filter.PROBLEMS -> it.state in CatalogLabels.PROBLEMS
            }
        }.map { snapshot -> Row(plate(snapshot), go { card(it, snapshot.product) }) }
        val title = filter.title.replaceFirstChar { it.uppercase() }
        list(player, title, rows, page, PAGE_SIZE, { who, to -> plugins(who, filter, to) }, { home(it) }, "Здесь пусто")
    }

    private fun card(player: Player, product: String) {
        val registration = library.updates.get(product) ?: return home(player)
        val snapshot = registration.snapshot
        val builder = frame(Menus.chest(title(snapshot.product)).rows(CARD_ROWS), CARD_ROWS)
        builder.slot(4, plate(snapshot, withHint = false))
        val update = if (snapshot.state == UpdateState.UPDATE_AVAILABLE) {
            item(
                "EMERALD", name("Обновить"),
                listOf(
                    Style.description("Подготовить версию ${snapshot.latestVersion}."), Style.description("Применится после перезапуска."),
                    "", Style.LEFT + "подготовить",
                ),
            )
        } else {
            item("COAL", name("Обновить", Style.MUTED), listOf(Style.description("Сейчас нет доступного обновления.")))
        }
        builder.button(10, update, go { allowed(it, DOWNLOAD) { it.closeInventory(); it.performCommand("pn update ${snapshot.product}") } })
        builder.button(
            11,
            item("COMPASS", name("Проверить"), listOf(Style.description("Проверить обновления этого плагина."), "", Style.LEFT + "проверить")),
            go {
                registration.checkNow()
                it.sendMessage("§7Проверка запущена. Откройте карточку снова через несколько секунд.")
                it.closeInventory()
            },
        )
        builder.button(12, autoItem(snapshot), go { who -> allowed(who, SETTINGS) { toggleAuto(who, snapshot) } })
        builder.button(
            13,
            item(
                "LEVER", name("Канал"),
                listOf(Style.parameter("Сейчас"), Style.value(snapshot.channel.name.lowercase()), "", Style.LEFT + "выбрать канал"),
            ),
            go { channels(it, snapshot.product) },
        )
        builder.button(14, holdItem(snapshot), go { holds(it, snapshot.product) })
        builder.button(
            15,
            item(
                "BOOKSHELF", name("Релизы"),
                listOf(Style.parameter("Известных версий"), Style.value("${snapshot.availableReleases.size}"), "", Style.LEFT + "открыть"),
            ),
            go { releases(it, snapshot.product, 0) },
        )
        builder.button(16, nav("BOOK", "История", "Записи только этого плагина."), go { history(it, snapshot.product, 0) })
        snapshot.latestChangelogUrl?.let { link ->
            builder.button(
                22,
                item("WRITTEN_BOOK", name("Список изменений"), listOf(Style.description("Ссылка придёт в чат."), "", Style.LEFT + "получить ссылку")),
                go { it.sendMessage("§7Список изменений: §f$link"); it.closeInventory() },
            )
        }
        builder.button(CARD_BACK, backItem(), go { plugins(it, Filter.ALL, 0) })
        show(player, builder.build())
    }

    private fun channels(player: Player, product: String) {
        val current = library.updates.get(product)?.snapshot?.channel
        val builder = frame(Menus.chest(title("Канал $product")).rows(SMALL_ROWS), SMALL_ROWS)
        UpdateChannel.values().forEachIndexed { index, channel ->
            val chosen = channel == current
            val lore = listOf(Style.description(CHANNEL_HINTS.getValue(channel)), "") +
                if (chosen) listOf(Style.value("выбран сейчас")) else listOf(Style.LEFT + "выбрать")
            builder.button(
                10 + index,
                item(CHANNEL_ITEMS.getValue(channel), name(channel.name.lowercase(), if (chosen) Style.GOOD else Style.NAME), lore),
                go { who -> allowed(who, SETTINGS) { chooseChannel(who, product, channel) } },
            )
        }
        builder.button(
            16,
            item("BARRIER", name("Сбросить", Style.MUTED), listOf(Style.description("Вернуть канал, который указал плагин."), "", Style.LEFT + "сбросить")),
            go { who -> allowed(who, SETTINGS) { chooseChannel(who, product, null) } },
        )
        builder.button(SMALL_BACK, backItem(), go { card(it, product) })
        show(player, builder.build())
    }

    private fun holds(player: Player, product: String) {
        val held = library.updates.get(product)?.snapshot?.state == UpdateState.FROZEN
        val builder = frame(Menus.chest(title("Пауза $product")).rows(SMALL_ROWS), SMALL_ROWS)
        HOLDS.forEachIndexed { index, (label, duration) ->
            builder.button(
                10 + index,
                item(
                    "CLOCK", name(label),
                    listOf(Style.description("Обновления не предлагаются и не скачиваются."), "", Style.LEFT + "приостановить"),
                    "WATCH",
                ),
                go { who -> allowed(who, SETTINGS) { chooseHold(who, product, duration) } },
            )
        }
        if (held) {
            builder.button(
                16,
                item("REDSTONE", name("Снять паузу", Style.GOOD), listOf(Style.description("Вернуть обновления сейчас."), "", Style.LEFT + "снять")),
                go { who -> allowed(who, SETTINGS) { chooseHold(who, product, null) } },
            )
        }
        builder.button(SMALL_BACK, backItem(), go { card(it, product) })
        show(player, builder.build())
    }

    private fun releases(player: Player, product: String, page: Int) {
        val snapshot = library.updates.get(product)?.snapshot ?: return home(player)
        val rows = snapshot.availableReleases.map { release ->
            val current = release.version == snapshot.currentVersion
            val link = release.changelogUrl
            val lore = buildList {
                add(Style.parameter("Канал")); add(Style.value(release.channel.name.lowercase()))
                release.publishedAt?.let { add(Style.parameter("Выпущен")); add(Style.value(TIME.format(it))) }
                if (current) add(Style.value("установлена сейчас"))
                if (link != null) { add(""); add(Style.LEFT + "получить список изменений") }
            }
            val click = link?.let { url -> go { it.sendMessage("§7Список изменений ${release.version}: §f$url"); it.closeInventory() } }
            Row(item(if (current) "EMERALD" else "PAPER", name(release.version, if (current) Style.GOOD else Style.NAME), lore), click)
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
                val rows = if (error != null) emptyList() else entries.map { Row(historyItem(it)) }
                val back: (Player) -> Unit = if (product == null) { who -> home(who) } else { who -> card(who, product) }
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
        val plain = listOf(
            Style.description("Вернутся сохранённые JAR."), Style.description("Конфигурации не изменятся."),
            Style.description("Применится после перезапуска."), "", Style.LEFT + "откатить",
        )
        builder.button(11, item("TNT", name("Откатить плагины", Style.DANGER), plain), go { who ->
            allowed(who, ROLLBACK) { rollback(who, backup, restoreConfigurations = false) }
        })
        if (backup.configurationFiles > 0) {
            val full = listOf(
                Style.description("Вернутся JAR и файлы настроек: ${backup.configurationFiles}."),
                Style.description("Применится после перезапуска."), "", Style.LEFT + "откатить",
            )
            builder.button(13, item("TNT", name("Откатить плагины и настройки", Style.DANGER), full), go { who ->
                allowed(who, ROLLBACK) { rollback(who, backup, restoreConfigurations = true) }
            })
        }
        builder.button(SMALL_BACK, backItem("Отмена"), go { backups(it, 0) })
        show(player, builder.build())
    }

    // --- actions -------------------------------------------------------------------------------------------------

    private fun checkAll(player: Player) {
        player.closeInventory()
        player.sendMessage("§7Проверяю обновления…")
        library.updates.checkNow(replies.updateActor(player)).whenComplete { _, error ->
            replies.onServerThread {
                if (!player.isOnline) return@onServerThread
                if (error != null) player.sendMessage("§cПроверка не удалась: ${error.cause?.message ?: error.message}")
                else home(player)
            }
        }
    }

    private fun toggleAuto(player: Player, snapshot: UpdateSnapshot) {
        library.updates.setAutomaticDownload(snapshot.product, !snapshot.automaticDownload, replies.updateActor(player))
            .whenComplete { _, _ -> replies.onServerThread { card(player, snapshot.product) } }
    }

    private fun chooseChannel(player: Player, product: String, channel: UpdateChannel?) {
        library.updates.setChannel(product, channel, replies.updateActor(player))
            .whenComplete { _, _ -> replies.onServerThread { card(player, product) } }
    }

    private fun chooseHold(player: Player, product: String, duration: Duration?) {
        library.updates.setHeld(product, duration, replies.updateActor(player))
            .whenComplete { _, _ -> replies.onServerThread { card(player, product) } }
    }

    private fun rollback(player: Player, backup: UpdateBackup, restoreConfigurations: Boolean) {
        player.closeInventory()
        library.updates.rollback(backup.id, restoreConfigurations, replies.updateActor(player)).whenComplete { _, error ->
            replies.onServerThread {
                if (!player.isOnline) return@onServerThread
                if (error != null) player.sendMessage("§cНе удалось подготовить откат: ${error.cause?.message ?: error.message}")
                else player.sendMessage("§aРезервный набор подготовлен. Полностью перезапустите сервер, чтобы применить его.")
            }
        }
    }

    private fun support(player: Player) {
        player.closeInventory()
        player.sendMessage("§7Если возникли проблемы, пишите нам: §f${PnLibraryIdentity.SUPPORT_URL}")
    }

    /** Runs [action] when [player] holds [permission], otherwise says what is missing. */
    private fun allowed(player: Player, permission: String, action: () -> Unit) {
        if (player.hasPermission(permission) || player.hasPermission("pnlibrary.admin")) action()
        else player.sendMessage("§cНе хватает права $permission.")
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
    ) {
        val pages = ((total - 1) / perPage + 1).coerceAtLeast(1)
        val current = page.coerceIn(0, pages - 1)
        val shown = if (total == rows.size) rows.drop(current * perPage).take(perPage) else rows.take(perPage)
        val builder = Menus.chest(title("$title ${current + 1}/$pages")).rows(LIST_ROWS)
        shown.forEachIndexed { slot, row -> if (row.click != null) builder.button(slot, row.stack, row.click) else builder.slot(slot, row.stack) }
        if (total == 0) builder.slot(22, item("BARRIER", name(emptyText, Style.MUTED)))
        footerCorners(builder)
        builder.fill(pane(), 0, FOOTER_END)
        builder.button(BACK, backItem(), go { back(it) })
        if (current > 0) {
            val previous = item("ARROW", name("Назад по списку"), listOf(Style.LEFT + "страница $current"))
            builder.button(PREVIOUS, previous, go { reopen(it, current - 1) })
        }
        if (current < pages - 1) {
            val following = item("ARROW", name("Дальше по списку"), listOf(Style.LEFT + "страница ${current + 2}"))
            builder.button(NEXT, following, go { reopen(it, current + 1) })
        }
        builder.button(CLOSE, closeItem(), go { it.closeInventory() })
        show(player, builder.build())
    }

    private fun go(action: (Player) -> Unit) = MenuClickHandler { action(it.player) }

    private fun show(player: Player, menu: Menu) {
        library.platform<BukkitPlatform>().menus.open(plugin, player, menu)
        OPEN_SOUND?.let { player.playSound(player.location, it, 1f, 1f) }
    }

    private fun snapshots(): List<UpdateSnapshot> = library.updates.all().map { it.snapshot }.sortedBy { it.product.lowercase() }

    private fun plate(snapshot: UpdateSnapshot, withHint: Boolean = true): ItemStack {
        val material = when (snapshot.state) {
            UpdateState.UP_TO_DATE -> "EMERALD"
            UpdateState.UPDATE_AVAILABLE -> "GOLD_INGOT"
            UpdateState.UPDATE_STAGED, UpdateState.DOWNLOADING, UpdateState.ROLLED_BACK -> "DIAMOND"
            UpdateState.FROZEN -> "CLOCK"
            UpdateState.CHECKING -> "COMPASS"
            UpdateState.FAILED, UpdateState.BLOCKED, UpdateState.INCOMPATIBLE -> "BARRIER"
        }
        val blockers = if (snapshot.state == UpdateState.BLOCKED) {
            library.updates.currentPlan().orElse(null)?.blockers.orEmpty().take(BLOCKER_LINES).map { Style.danger(BukkitUpdateMessages.blocker(it)) }
        } else {
            emptyList()
        }
        val lore = buildList {
            add(Style.parameter("Версия")); add(Style.value(snapshot.currentVersion))
            snapshot.latestVersion?.let { add(Style.parameter("Последняя")); add(Style.value(it)) }
            add(Style.parameter("Канал")); add(Style.value(snapshot.channel.name.lowercase()))
            add(Style.parameter("Состояние")); add(Style.value(CatalogLabels.stateLabel(snapshot.state)))
            add(Style.parameter("Автозагрузка")); add(Style.value(if (snapshot.automaticDownload) "включена" else "выключена"))
            snapshot.supportedApi?.let { add(Style.parameter("API pnLibrary")); add(Style.value("${it.minimum}–${it.maximum}")) }
            snapshot.message?.takeIf { snapshot.state in CatalogLabels.PROBLEMS }?.let { add(Style.danger(it)) }
            addAll(blockers)
            if (withHint) {
                add("")
                add(Style.LEFT + "открыть карточку")
            }
        }
        return item(material, name(snapshot.product), lore, "WATCH".takeIf { material == "CLOCK" })
    }

    private fun autoItem(snapshot: UpdateSnapshot): ItemStack = item(
        "REDSTONE_TORCH", name("Автозагрузка"),
        listOf(
            Style.parameter("Сейчас"), Style.value(if (snapshot.automaticDownload) "включена" else "выключена"),
            "", Style.LEFT + "переключить",
        ),
        "REDSTONE_TORCH_ON",
    )

    private fun holdItem(snapshot: UpdateSnapshot): ItemStack {
        val held = snapshot.state == UpdateState.FROZEN
        val lore = listOf(
            Style.description(if (held) "Обновления сейчас приостановлены." else "Приостановить обновления на время."), "",
            Style.LEFT + if (held) "изменить или снять" else "выбрать срок",
        )
        return item("CLOCK", name("Пауза", if (held) Style.WARN else Style.NAME), lore, "WATCH")
    }

    private fun historyItem(entry: UpdateHistoryEntry): ItemStack {
        val failed = entry.state == UpdateState.FAILED
        val lore = buildList {
            add(Style.parameter("Когда")); add(Style.value(TIME.format(entry.occurredAt)))
            add(Style.parameter("Кто")); add(Style.value(entry.actor.name))
            entry.changes.forEach { add(Style.parameter(it.product.value)); add(Style.value("${it.from} » ${it.to}")) }
            entry.message?.let { add(if (failed) Style.danger(it) else Style.description(it)) }
        }
        val title = "${CatalogLabels.operationLabel(entry.operation)}: ${CatalogLabels.stateLabel(entry.state)}"
        return item(if (failed) "BARRIER" else "PAPER", name(title, if (failed) Style.DANGER else Style.NAME), lore)
    }

    private fun backupItem(backup: UpdateBackup, withHint: Boolean = true): ItemStack {
        val lore = buildList {
            backup.createdAt?.let { add(Style.parameter("Создана")); add(Style.value(TIME.format(it))) }
            backup.components.forEach {
                add(Style.parameter(it.product.value))
                add(Style.value("${it.backupVersion ?: "?"} (было ${it.updatedVersion ?: "?"})"))
            }
            if (backup.configurationFiles > 0) { add(Style.parameter("Файлов настроек")); add(Style.value("${backup.configurationFiles}")) }
            if (withHint) { add(""); add(Style.LEFT + "выбрать откат") }
        }
        return item("CHEST", name("Резервный набор"), lore)
    }

    private fun nav(material: String, label: String, description: String, alternative: String? = null): ItemStack =
        item(material, name(label), listOf(Style.description(description), "", Style.LEFT + "открыть"), alternative)

    private fun backItem(label: String = "Вернуться"): ItemStack = item("ARROW", name(label, Style.BACK), listOf(Style.LEFT + "назад"))

    private fun closeItem(): ItemStack = item("BARRIER", name("Закрыть", Style.BACK), listOf(Style.LEFT + "закрыть меню"))

    private fun title(text: String): String = "pnLibrary | $text"

    private fun name(text: String, color: String = Style.NAME): String = color + text

    /** Light-blue corner brackets on the four corners of a [rows]-row menu, then the dark glass between them. */
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

    private fun accent(): ItemStack = glass("LIGHT_BLUE_STAINED_GLASS_PANE", LEGACY_LIGHT_BLUE)

    private fun glass(modern: String, legacyData: Short): ItemStack {
        val stack = item(modern, "§r", emptyList(), "STAINED_GLASS_PANE")
        if (stack.type.name == "STAINED_GLASS_PANE") stack.durability = legacyData
        return stack
    }

    private fun item(material: String, name: String, lore: List<String> = emptyList(), alternative: String? = null): ItemStack {
        val type = listOfNotNull(material, alternative).firstNotNullOfOrNull { Material.matchMaterial(it) } ?: Material.PAPER
        return ItemStack(type).also { stack ->
            stack.itemMeta?.let { meta ->
                meta.setDisplayName(name)
                meta.lore = lore
                stack.itemMeta = meta
            }
        }
    }

    /** Colours and hint lines of the menu, in the style of the organisation's other plugins. */
    private object Style {
        val NAME = hex("5FD7FF", "§b")
        val GOOD = hex("A7F3D0", "§a")
        val WARN = hex("FFB347", "§e")
        val DANGER = hex("FC7165", "§c")
        val MUTED = hex("69747E", "§8")
        val BACK = DANGER
        private val DESCRIPTION = hex("A6B0BA", "§7")
        private val PARAMETER = hex("C2CBD3", "§f")
        private val ARROW = hex("69747E", "§8") + "↳ "
        private val VALUE = hex("A7F3D0", "§a")

        /** The orange "left click" hint with square brackets, followed by the action in white. */
        val LEFT = hex("FFB347", "§6") + "[ЛКМ] §f"

        fun description(text: String) = DESCRIPTION + text
        fun parameter(text: String) = PARAMETER + text
        fun value(text: String) = ARROW + VALUE + text
        fun danger(text: String) = DANGER + text

        /** `§x§R§R§G§G§B§B` on servers that know hex colours, the nearest legacy code otherwise. */
        private fun hex(rgb: String, legacy: String): String =
            if (HEX_SUPPORTED) "§x" + rgb.map { "§$it" }.joinToString("") else legacy
    }

    private companion object {
        const val DOWNLOAD = "pnlibrary.updates.download"
        const val SETTINGS = "pnlibrary.updates.settings"
        const val ROLLBACK = "pnlibrary.updates.rollback"
        const val HOME_ROWS = 4
        const val HOME_CLOSE = 31
        const val CARD_ROWS = 4
        const val CARD_BACK = 31
        const val SMALL_ROWS = 3
        const val SMALL_BACK = 22
        const val LIST_ROWS = 6
        const val PAGE_SIZE = 45
        const val HISTORY_PAGE = 10
        const val FOOTER_START = 45
        const val FOOTER_END = 54
        const val BACK = 45
        const val PREVIOUS = 48
        const val NEXT = 50
        const val CLOSE = 53
        const val BLOCKER_LINES = 3
        const val LEGACY_BLACK: Short = 15
        const val LEGACY_LIGHT_BLUE: Short = 3
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault())

        /** True when the server's chat accepts `§x` hex colours (1.16 and newer). */
        val HEX_SUPPORTED: Boolean = runCatching { Class.forName("net.md_5.bungee.api.ChatColor").getMethod("of", String::class.java) }.isSuccess

        val OPEN_SOUND: Sound? = listOf("BLOCK_BARREL_OPEN", "BLOCK_CHEST_OPEN", "CHEST_OPEN")
            .firstNotNullOfOrNull { name -> Sound.values().firstOrNull { it.name == name } }

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
