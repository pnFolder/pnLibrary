package org.pnfolder.pnlibrary.core.menu

import org.pnfolder.pnlibrary.api.chat.ChatTone
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity
import org.pnfolder.pnlibrary.api.updates.UpdateChannel
import org.pnfolder.pnlibrary.api.updates.UpdateSnapshot
import org.pnfolder.pnlibrary.api.updates.UpdateState
import org.pnfolder.pnlibrary.core.updates.catalog.CatalogLabels
import org.pnfolder.pnlibrary.core.updates.catalog.UpdateCatalogView
import java.time.Duration
import java.time.Instant

internal enum class Filter(val title: String) { ALL("Плагины"), UPDATES("Обновления"), PROBLEMS("Проблемы") }

internal class UpdateScreens(session: MenuSession, routes: MenuRoutes) : MenuScreens(session, routes) {
    fun plugins(player: MenuViewer, filter: Filter, page: Int) {
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
            Filter.PROBLEMS -> shown + routes.journalRows("error") { who -> plugins(who, filter, page) }
            Filter.UPDATES -> shown
        }
        list(player, filter.title, rows, page, PAGE_SIZE, { who, to -> plugins(who, filter, to) }, { routes.home(it) }, "Здесь пусто")
    }

    fun card(player: MenuViewer, product: String) {
        val snapshot = data.snapshot(product) ?: return routes.home(player)
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
        builder.button(10, update, go { allowed(it, DOWNLOAD) { it.close(); runCommand(it, "pn update ${snapshot.product}") } })
        builder.button(
            11,
            icon(Icons.CHECK, "COMPASS", "Проверить", Style.lore(listOf("Проверить обновления этого плагина."), emptyList(), Style.left("проверить"))),
            go { checkOne(it, snapshot.product) },
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
        builder.button(16, section(Icons.HISTORY, "BOOK", "История", "Записи только этого плагина."), go { routes.history(it, snapshot.product, 0) })
        snapshot.latestChangelogUrl?.let { link ->
            builder.button(
                22,
                icon(Icons.CHANGELOG, "WRITTEN_BOOK", "Список изменений",
                    Style.lore(listOf("Что нового в последней версии."), emptyList(), Style.left("получить ссылку"))),
                go { who -> who.close(); tell(who, ChatTone.INFO, "Список изменений ${snapshot.product}", link) },
            )
        }
        builder.button(CARD_BACK, backItem(), go { plugins(it, Filter.ALL, 0) })
        show(player, builder.build())
    }

    /** The channel choice. It stays in the code and is switched on by [CHANNEL_SWITCH] once more than `dev` works. */
    private fun channels(player: MenuViewer, product: String) {
        val current = data.snapshot(product)?.channel
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

    private fun holds(player: MenuViewer, product: String) {
        val until = data.heldUntil(product)?.takeIf { it.isAfter(Instant.now()) }
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

    /** The index of the shortest pause in [HOLDS] that is at least [remaining]; the last one when none is. */
    private fun holdIndex(remaining: Duration): Int = HOLDS.indexOfFirst { it.second >= remaining }.takeIf { it >= 0 } ?: (HOLDS.size - 1)

    private fun releases(player: MenuViewer, product: String, page: Int) {
        val snapshot = data.snapshot(product) ?: return routes.home(player)
        val rows = snapshot.availableReleases.map { release ->
            val current = release.version == snapshot.currentVersion
            val link = release.changelogUrl
            val parameters = buildList {
                add("Канал" to release.channel.name.lowercase())
                release.publishedAt?.let { add("Выпущен" to time(it)) }
                if (current) add("Состояние" to "установлена сейчас")
            }
            val lore = Style.lore(emptyList(), parameters, *if (link != null) arrayOf(Style.left("получить список изменений")) else emptyArray())
            val click = link?.let { url -> go { who -> who.close(); tell(who, ChatTone.INFO, "Список изменений ${release.version}", url) } }
            Row(icon(null, if (current) "EMERALD" else "PAPER", release.version, lore, good = current), click)
        }
        list(player, "Релизы $product", rows, page, PAGE_SIZE, { who, to -> releases(who, product, to) }, { card(it, product) }, "Релизов пока нет")
    }

    fun checkAll(player: MenuViewer) {
        player.close()
        tell(player, ChatTone.PROGRESS, "Проверяю обновления…")
        data.checkAll(player.actor).whenComplete { _, error ->
            sync {
                if (!player.online) return@sync
                if (error != null) return@sync tell(player, ChatTone.FAILURE, "Не удалось проверить обновления.", reason(error))
                val snapshots = snapshots()
                val news = UpdateCatalogView.announcement(snapshots, ROUTES, PnLibraryIdentity.SUPPORT_URL)
                if (news.isEmpty()) tell(player, ChatTone.SUCCESS, "Проверка завершена: все плагины используют актуальные версии.")
                else deliver(player, news)
            }
        }
    }

    private fun checkOne(player: MenuViewer, product: String) {
        player.close()
        tell(player, ChatTone.PROGRESS, "Проверяю обновления $product…")
        data.checkOne(product, player.actor)
        later(CHECK_REOPEN_MILLIS) { if (player.online) card(player, product) }
    }

    private fun toggleAuto(player: MenuViewer, snapshot: UpdateSnapshot) {
        val enable = !snapshot.automaticDownload
        data.setAutomatic(snapshot.product, enable, player.actor).whenComplete { _, error ->
            sync {
                if (error != null) tell(player, ChatTone.FAILURE, "Не удалось изменить автозагрузку.", reason(error))
                else tell(player, ChatTone.SUCCESS, "Автозагрузка ${snapshot.product}: ${if (enable) "включена" else "выключена"}.")
                card(player, snapshot.product)
            }
        }
    }

    private fun chooseChannel(player: MenuViewer, product: String, channel: UpdateChannel?) {
        data.setChannel(product, channel, player.actor).whenComplete { _, error ->
            sync {
                if (error != null) tell(player, ChatTone.FAILURE, "Не удалось изменить канал.", reason(error))
                else tell(player, ChatTone.SUCCESS, "Канал $product: ${channel?.name?.lowercase() ?: "как указал плагин"}.")
                card(player, product)
            }
        }
    }

    private fun chooseHold(player: MenuViewer, product: String, duration: Duration?) {
        data.setHeld(product, duration, player.actor).whenComplete { _, error ->
            sync {
                if (error != null) tell(player, ChatTone.FAILURE, "Не удалось изменить паузу.", reason(error))
                else tell(player, ChatTone.SUCCESS, if (duration == null) "Пауза $product снята." else "Обновления $product приостановлены.")
                card(player, product)
            }
        }
    }

    /** Runs [change] for every registered plugin, then reports [done] and reopens the settings. */
    fun everyone(player: MenuViewer, done: String, change: (UpdateSnapshot) -> java.util.concurrent.CompletionStage<*>) {
        val stages = snapshots().map { change(it).toCompletableFuture() }
        java.util.concurrent.CompletableFuture.allOf(*stages.toTypedArray()).whenComplete { _, error ->
            sync {
                if (error != null) tell(player, ChatTone.FAILURE, "Не всё удалось изменить.", reason(error))
                else tell(player, ChatTone.SUCCESS, done)
                routes.settings(player)
            }
        }
    }

    fun setAutomatic(snapshot: UpdateSnapshot, enable: Boolean, player: MenuViewer) =
        data.setAutomatic(snapshot.product, enable, player.actor)

    fun hold(snapshot: UpdateSnapshot, duration: Duration?, player: MenuViewer) =
        data.setHeld(snapshot.product, duration, player.actor)

    /** Plugins connected to the library that have no update registration: they are listed so none is missing. */
    private fun unmanaged(known: Set<String>): List<Row> = data.plugins()
        .filter { it.name.lowercase() !in known }
        .sortedBy { it.name.lowercase() }
        .map { plugin ->
            val parameters = buildList {
                plugin.version?.let { add("Версия" to it) }
                add("Модулей" to "${plugin.modules}")
                add("Обновления" to "плагин не подключён к системе обновлений")
            }
            Row(icon(Icons.PLUGIN, "PAPER", plugin.name, Style.lore(emptyList(), parameters)))
        }

    private fun plate(snapshot: UpdateSnapshot, withHint: Boolean = true, hints: Boolean = false): ItemSpec {
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
            data.plan()?.blockers.orEmpty().take(BLOCKER_LINES)
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

    private fun autoItem(snapshot: UpdateSnapshot): ItemSpec = icon(
        Icons.TOGGLE, "REDSTONE_TORCH", "Автозагрузка",
        Style.lore(
            listOf("Готовить новые версии автоматически."),
            listOf("Сейчас" to if (snapshot.automaticDownload) "включена" else "выключена"),
            Style.left("переключить"),
        ),
        alternative = "REDSTONE_TORCH_ON", good = snapshot.automaticDownload,
    )

    private fun holdItem(snapshot: UpdateSnapshot): ItemSpec {
        val held = snapshot.state == UpdateState.FROZEN
        val lore = Style.lore(
            listOf(if (held) "Обновления сейчас приостановлены." else "Приостановить обновления на время."),
            emptyList(), Style.left(if (held) "изменить или снять" else "выбрать срок"),
        )
        return icon(Icons.CLOCK, "CLOCK", "Пауза", lore, alternative = "WATCH", warn = held)
    }
}
