package org.pnfolder.pnlibrary.bukkit.commands

import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.pnfolder.pnlibrary.api.chat.ChatNotice
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
import org.pnfolder.pnlibrary.core.updates.catalog.UpdateCatalogView
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
 * same, and every answer is a chat notice in the library's own style. Buttons are textured heads with an ordinary
 * item as the fallback, so the menu opens on every server from 1.8.
 */
internal class PluginsMenu(
    private val plugin: Plugin,
    private val library: PnLibrary,
    private val replies: BukkitCommandReplies,
) {
    private enum class Filter(val title: String) { ALL("Плагины"), UPDATES("Обновления"), PROBLEMS("Проблемы") }

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
        builder.slot(
            4,
            icon(
                Icons.OVERVIEW, "NETHER_STAR", "${PnLibraryIdentity.NAME} ${library.version}",
                Style.lore(
                    listOf("Библиотека для плагинов pnFolder: обновления, настройки и поддержка."),
                    listOf("Плагинов подключено" to "${connectedCount()}", "Обновлений" to "$pending", "Проблем" to "$problems"),
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
        builder.button(31, section(Icons.BACKUPS, "CHEST", "Резервные копии", "Откат к сохранённым версиям плагинов."), go { backups(it, 0) })
        builder.button(
            33,
            icon(Icons.CHECK, "COMPASS", "Проверить всё", Style.lore(listOf("Спросить каталоги релизов прямо сейчас."), emptyList(), Style.left("проверить"))),
            go { checkAll(it) },
        )
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
        }.map { snapshot -> Row(plate(snapshot), go { card(it, snapshot.product) }) }
        val rows = if (filter == Filter.ALL) shown + unmanaged(updatable.map { it.product.lowercase() }.toSet()) else shown
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
        builder.button(
            13,
            section(Icons.CHANNEL, "LEVER", "Канал", "Какие версии предлагать: стабильные или ранние.", "Сейчас" to snapshot.channel.name.lowercase()),
            go { channels(it, snapshot.product) },
        )
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
            icon(Icons.RESET, "BARRIER", "Сбросить",
                Style.lore(listOf("Вернуть канал, который указал плагин."), emptyList(), Style.left("сбросить")), muted = true),
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
                icon(
                    Icons.CLOCK, "CLOCK", label,
                    Style.lore(listOf("Обновления не предлагаются и не скачиваются."), emptyList(), Style.left("приостановить")),
                    alternative = "WATCH",
                ),
                go { who -> allowed(who, SETTINGS) { chooseHold(who, product, duration) } },
            )
        }
        if (held) {
            builder.button(
                16,
                icon(Icons.RESET, "REDSTONE", "Снять паузу", Style.lore(listOf("Вернуть обновления сейчас."), emptyList(), Style.left("снять")), good = true),
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
            val parameters = buildList {
                add("Канал" to release.channel.name.lowercase())
                release.publishedAt?.let { add("Выпущен" to TIME.format(it)) }
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
        builder.button(CLOSE, closeItem(), go { it.closeInventory() })
        show(player, builder.build())
    }

    private fun go(action: (Player) -> Unit) = MenuClickHandler { action(it.player) }

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

    private fun plate(snapshot: UpdateSnapshot, withHint: Boolean = true): ItemStack {
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
            add("Состояние" to CatalogLabels.stateLabel(snapshot.state))
            add("Автозагрузка" to if (snapshot.automaticDownload) "включена" else "выключена")
            snapshot.supportedApi?.let { add("API pnLibrary" to "${it.minimum}–${it.maximum}") }
        }
        val problems = (listOfNotNull(snapshot.message.takeIf { problem }) + blockers).map { Style.danger(it) }
        val lore = Style.lore(emptyList(), parameters, *if (withHint) arrayOf(Style.left("открыть карточку")) else emptyArray(), extra = problems)
        return icon(null, material, snapshot.product, lore, alternative = "WATCH".takeIf { material == "CLOCK" }, danger = problem)
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

    private fun historyItem(entry: UpdateHistoryEntry): ItemStack {
        val failed = entry.state == UpdateState.FAILED
        val parameters = buildList {
            add("Когда" to TIME.format(entry.occurredAt))
            add("Кто" to entry.actor.name)
            entry.changes.forEach { add(it.product.value to "${it.from} » ${it.to}") }
        }
        val note = listOfNotNull(entry.message?.let { if (failed) Style.danger(it) else Style.description(it) })
        val title = "${CatalogLabels.operationLabel(entry.operation)}: ${CatalogLabels.stateLabel(entry.state)}"
        return icon(null, if (failed) "BARRIER" else "PAPER", title, Style.lore(emptyList(), parameters, extra = note), danger = failed)
    }

    private fun backupItem(backup: UpdateBackup, withHint: Boolean = true): ItemStack {
        val parameters = buildList {
            backup.createdAt?.let { add("Создана" to TIME.format(it)) }
            backup.components.forEach { add(it.product.value to "${it.backupVersion ?: "?"} (было ${it.updatedVersion ?: "?"})") }
            if (backup.configurationFiles > 0) add("Файлов настроек" to "${backup.configurationFiles}")
        }
        val lore = Style.lore(emptyList(), parameters, *if (withHint) arrayOf(Style.left("выбрать откат")) else emptyArray())
        return icon(Icons.BACKUPS, "CHEST", "Резервный набор", lore)
    }

    private fun section(texture: String?, material: String, label: String, description: String, vararg parameters: Pair<String, String>): ItemStack =
        icon(texture, material, label, Style.lore(listOf(description), parameters.toList(), Style.left("открыть")))

    private fun backItem(label: String = "Вернуться"): ItemStack =
        icon(Icons.BACK, "ARROW", label, Style.lore(emptyList(), emptyList(), Style.left("назад")), danger = true)

    private fun closeItem(): ItemStack =
        icon(Icons.BACK, "BARRIER", "Закрыть", Style.lore(emptyList(), emptyList(), Style.left("закрыть меню")), danger = true)

    private fun title(text: String): String = "§0pnLibrary | $text"

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
        val stack = item(modern, " ", emptyList(), "STAINED_GLASS_PANE")
        if (stack.type.name == "STAINED_GLASS_PANE") stack.durability = legacyData
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
        val color = when {
            danger -> Style.DANGER
            warn -> Style.WARN
            good -> Style.GOOD
            muted -> Style.MUTED
            else -> Style.NAME
        }
        val head = texture?.let { MenuHeads.head(it) }
        return decorate(head ?: item(material, color + label, emptyList(), alternative), color + label, lore)
    }

    private fun item(material: String, name: String, lore: List<String> = emptyList(), alternative: String? = null): ItemStack {
        val type = listOfNotNull(material, alternative).firstNotNullOfOrNull { Material.matchMaterial(it) } ?: Material.PAPER
        return decorate(ItemStack(type), name, lore)
    }

    private fun decorate(stack: ItemStack, name: String, lore: List<String>): ItemStack = stack.also {
        it.itemMeta?.let { meta ->
            meta.setDisplayName(name)
            meta.lore = lore
            ItemFlag.values().forEach { flag -> meta.addItemFlags(flag) }
            it.itemMeta = meta
        }
    }

    /** Colours and lines of the menu, in the style of the organisation's other plugins. */
    private object Style {
        val NAME = MenuHeads.color("&#5FD7FF")
        val GOOD = MenuHeads.color("&#A7F3D0")
        val WARN = MenuHeads.color("&#FFB347")
        val DANGER = MenuHeads.color("&#FC7165")
        val MUTED = MenuHeads.color("&#9AA4AD")
        private val DESCRIPTION = MenuHeads.color(" &#A6B0BA")
        private val PARAMETER = MenuHeads.color(" &#C2CBD3")
        private val VALUE = MenuHeads.color(" &#69747E↳ &#A7F3D0")
        private val LEFT = MenuHeads.color(" &#FFB347[&#FFA43C Л&#FF9536К&#FF8630М &#FF772A] &f")

        fun left(action: String): String = LEFT + action

        fun description(text: String): String = DESCRIPTION + text

        fun danger(text: String): String = MenuHeads.color(" &#FC7165") + text

        /** Description lines, a blank line, `label` / `↳ value` pairs, a blank line, then the click hints; framed by blank lines. */
        fun lore(
            lines: List<String>,
            parameters: List<Pair<String, String>>,
            vararg hints: String,
            extra: List<String> = emptyList(),
        ): List<String> = buildList {
            add("")
            lines.forEach { add(description(it)) }
            if (lines.isNotEmpty() && parameters.isNotEmpty()) add("")
            parameters.forEach { (label, value) -> add(PARAMETER + label); add(VALUE + value) }
            if (extra.isNotEmpty()) { add(""); addAll(extra) }
            if (hints.isNotEmpty()) { add(""); addAll(hints) }
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
        const val CHANNEL = "8426f715b80dd90eb1e1929f6ec4bc3583de8f821bdd4f4c1722e69c98b50506"
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
        const val CHECK_REOPEN_TICKS = 60L
        const val SOUND_VOLUME = 0.22f
        const val SOUND_PITCH = 1.15f
        const val LEGACY_BLACK: Short = 15
        const val LEGACY_LIGHT_BLUE: Short = 3
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault())

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
