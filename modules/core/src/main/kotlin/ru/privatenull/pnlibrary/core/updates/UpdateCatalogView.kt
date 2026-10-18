package ru.privatenull.pnlibrary.core.updates

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import ru.privatenull.pnlibrary.api.updates.UpdateBackup
import ru.privatenull.pnlibrary.api.updates.UpdateChannel
import ru.privatenull.pnlibrary.api.updates.UpdateHistoryEntry
import ru.privatenull.pnlibrary.api.updates.UpdateSnapshot
import ru.privatenull.pnlibrary.api.updates.UpdateState
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Commands a catalog page links to, so one view serves both the Bukkit `/pn updates` tree and the
 * console-only `/pnupdate` command.
 *
 * @property root base of the catalog commands, for example `/pn updates`
 * @property update builds the command that prepares an update for one product; null hides update buttons
 * @property check command that refreshes the release catalogs
 * @property interactive whether the reader can click; without it buttons are replaced by a list of commands
 * @property ascii whether to avoid characters outside ASCII, for consoles with a legacy code page
 * @property editable whether the reader may change settings; otherwise the settings card is read-only
 */
data class UpdateCatalogRoutes(
    val root: String,
    val update: ((String) -> String)? = null,
    val check: String = "$root check",
    val interactive: Boolean = true,
    val ascii: Boolean = false,
    val editable: Boolean = true,
) {
    val list: String get() = "$root list"
    val available: String get() = "$root available"
    val history: String get() = "$root history"
    val backups: String get() = "$root backups"
    fun info(product: String): String = "$root info $product"
    fun settings(product: String): String = "$root settings $product"
    fun history(product: String): String = "$root history plugin $product"
    fun set(product: String, setting: String, value: String): String = "$root set $product $setting $value"
    fun rollback(id: Any, configurations: Boolean = false): String = "$root rollback $id" + if (configurations) " configs" else ""
}

/**
 * Shared chat and console presentation of the managed update catalog.
 *
 * Pages follow the calm layout of the Catalog plugin: a bold title with gray counters, an optional gray hint,
 * then blank-line separated sections of rows (white names, gray details, one colored state word) and a footer of
 * bracketed buttons whose brackets are gray and whose label carries the color. Details live in multi-line hover
 * cards so rows stay short enough for the default chat width. A reader that cannot click, such as the console
 * or a legacy server without the Adventure transport, gets the same rows without buttons and a list of the
 * commands instead; the console additionally gets ASCII only. Nothing here calls the server or changes
 * updates, and stored text is only ever rendered as plain text, never interpreted as markup.
 */
object UpdateCatalogView {
    /** Entries per page, shared with the history reader. */
    const val PAGE_SIZE = 10

    /** Channels a component can be switched between, in order of increasing risk. */
    val CHANNELS: List<UpdateChannel> = UpdateChannel.values().toList()

    /** Pause periods offered on the settings card, as accepted by `set <plugin> hold`; a pause lasts at most a week. */
    val HOLD_PERIODS: List<String> = listOf("1h", "6h", "1d", "3d", "7d")

    private val pending = setOf(UpdateState.UPDATE_AVAILABLE, UpdateState.UPDATE_STAGED, UpdateState.DOWNLOADING)
    private val problems = setOf(UpdateState.FAILED, UpdateState.BLOCKED, UpdateState.INCOMPATIBLE)
    private val russian = Locale("ru")
    private val dayFormat = DateTimeFormatter.ofPattern("d MMMM", russian)
    private val dayYearFormat = DateTimeFormatter.ofPattern("d MMMM uuuu", russian)
    private val clock = DateTimeFormatter.ofPattern("HH:mm")
    private val fullTime = DateTimeFormatter.ofPattern("d MMM HH:mm", russian)

    /**
     * Renders one page of registered components, never the native plugin inventory.
     *
     * @param now reference for release ages in the hover cards
     */
    fun catalog(
        snapshots: List<UpdateSnapshot>,
        page: Int,
        updatesOnly: Boolean,
        routes: UpdateCatalogRoutes,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Component> = page(Renderer(routes, now, zone).catalog(snapshots, page, updatesOnly))

    /**
     * Renders durable events newest first, grouped by day.
     *
     * @param totalPages number of pages when known; otherwise only [entries] being a full page implies another
     * @param product when set, the title shows the filter and the lines do not offer to filter again
     * @param now reference for "today" and "yesterday"
     * @param zone zone used for days and clock times
     */
    fun history(
        entries: List<UpdateHistoryEntry>,
        page: Int,
        routes: UpdateCatalogRoutes,
        totalPages: Int? = null,
        product: String? = null,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Component> = page(Renderer(routes, now, zone).history(entries, page, totalPages, product))

    /** Renders retained rollback sets with their age; paths and file contents are never shown. */
    fun backups(
        backups: List<UpdateBackup>,
        page: Int,
        routes: UpdateCatalogRoutes,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Component> = page(Renderer(routes, now, zone).backups(backups, page))

    /**
     * Renders the details card of one component: versions, release date, other channels and its pause.
     *
     * @param heldUntil when the current pause ends, from [ru.privatenull.pnlibrary.api.updates.UpdateService.heldUntil]
     */
    fun info(
        snapshot: UpdateSnapshot,
        routes: UpdateCatalogRoutes,
        heldUntil: Instant? = null,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Component> = page(Renderer(routes, now, zone).info(snapshot, heldUntil))

    /** Renders the settings card of one component: channel, automatic download and a pause for a chosen period. */
    fun settings(
        snapshot: UpdateSnapshot,
        routes: UpdateCatalogRoutes,
        heldUntil: Instant? = null,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Component> = page(Renderer(routes, now, zone).settings(snapshot, heldUntil))

    /** Renders one short message of an update command: a mark in the tone's color, the text and its detail. */
    fun notice(notice: UpdateNotice, routes: UpdateCatalogRoutes): List<Component> =
        Renderer(routes, Instant.now(), ZoneId.systemDefault()).notice(notice)

    /** Renders [notices] one after another, as a single reply. */
    fun notices(notices: List<UpdateNotice>, routes: UpdateCatalogRoutes): List<Component> =
        notices.flatMap { notice(it, routes) }

    /**
     * Renders a card that asks to confirm a risky action, such as a rollback or a restart.
     *
     * @param lines what will happen, one sentence per line
     * @param command the command that confirms; a reader that cannot click is told to type it
     */
    fun confirmation(
        title: String,
        lines: List<String>,
        label: String,
        command: String,
        hint: String,
        routes: UpdateCatalogRoutes,
    ): List<Component> = page(Renderer(routes, Instant.now(), ZoneId.systemDefault()).confirmation(title, lines, label, command, hint))

    /**
     * Renders the state of the server and every registered component, for `/pn status`.
     *
     * @param facts labelled values shown before the components, such as the server core and Java
     * @param support link opened by the support button, or null to hide it
     */
    fun status(
        facts: List<Pair<String, String>>,
        snapshots: List<UpdateSnapshot>,
        routes: UpdateCatalogRoutes,
        support: String? = null,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Component> = page(Renderer(routes, now, zone).status(facts, snapshots, support))

    /**
     * Renders the notice an administrator gets on joining: only the components with an available or prepared
     * update, or nothing when there are none.
     */
    fun announcement(
        snapshots: List<UpdateSnapshot>,
        routes: UpdateCatalogRoutes,
        support: String? = null,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Component> = Renderer(routes, now, zone).announcement(snapshots, support).let { if (it.isEmpty()) it else page(it) }

    /** A page starts with an empty line, so it never runs into the chat above it, such as the previous page. */
    private fun page(lines: List<Component>): List<Component> = listOf(Component.empty()) + lines

    /** What a history event led to: a short label for the row and the full wording for its hover card. */
    private class Outcome(val label: String, val color: TextColor, val detail: String = label)

    private class Renderer(private val routes: UpdateCatalogRoutes, private val now: Instant, private val zone: ZoneId) {
        private val clickable = routes.interactive && routes.editable

        // --- pages -------------------------------------------------------------------------------------------

        fun catalog(snapshots: List<UpdateSnapshot>, page: Int, updatesOnly: Boolean): List<Component> {
            require(page > 0) { "page must be positive" }
            val selected = snapshots.filter { !updatesOnly || it.state in pending }.sortedBy { it.product.lowercase() }
            val entries = selected.page(page)
            val result = mutableListOf(header(if (updatesOnly) "Обновления" else "Каталог", catalogSummary(snapshots)))
            if (routes.interactive && entries.isNotEmpty()) result += note("Нажмите на плагин, чтобы открыть его карточку.")
            result += blank()
            entries.forEach { result += catalogRows(it) }
            if (entries.isEmpty()) result += when {
                selected.isNotEmpty() -> note("На этой странице пусто.")
                updatesOnly -> text("  Все плагины актуальны.", UpdatePalette.OK)
                else -> note("Пока нет плагинов, зарегистрированных в pnLibrary.")
            }
            result += blank()
            actions(
                button("[Проверить]", routes.check, "Запросить новые версии у каталогов", UpdatePalette.OK),
                if (updatesOnly) button("[Все]", routes.list, "Все зарегистрированные плагины", UpdatePalette.ACCENT)
                else button("[Обновления]", routes.available, "Только доступные и подготовленные обновления", UpdatePalette.ACCENT),
                button("[История]", routes.history, "Кто, когда и что обновлял", UpdatePalette.ACCENT),
                button("[Резервы]", routes.backups, "Сохранённые версии для отката", UpdatePalette.ACCENT),
            )?.let { result += it }
            pager(page, pageCount(selected.size), if (updatesOnly) routes.available else routes.list, "Предыдущая", "Следующая")
                ?.let { result += it }
            if (!routes.interactive) {
                result += commandHints(
                    routes.check to "проверить обновления",
                    routes.available to "только обновления",
                    "${routes.root} info <плагин>" to "карточка плагина",
                    "${routes.root} settings <плагин>" to "настройки",
                    routes.history to "история",
                    routes.backups to "резервы",
                    routes.update?.invoke("<плагин>") to "обновить",
                )
            }
            return result
        }

        fun history(entries: List<UpdateHistoryEntry>, page: Int, totalPages: Int?, product: String?): List<Component> {
            require(page > 0) { "page must be positive" }
            val result = mutableListOf(header("История", product?.let { text(it, UpdatePalette.SOFT) }))
            var day: LocalDate? = null
            entries.forEach { entry ->
                val entryDay = entry.occurredAt.atZone(zone).toLocalDate()
                if (entryDay != day) {
                    day = entryDay
                    result += blank()
                    result += text("  ${dayLabel(entryDay)}", UpdatePalette.ACCENT)
                }
                result += historyRows(entry, product == null)
            }
            if (entries.isEmpty()) result += listOf(blank(), note(if (page == 1) "Событий пока нет." else "На этой странице пусто."))
            result += blank()
            val base = if (product == null) routes.history else routes.history(product)
            val pages = totalPages ?: if (entries.size >= PAGE_SIZE) page + 1 else page
            actions(
                product?.let { button("[Все события]", routes.history, "История всех плагинов", UpdatePalette.ACCENT) },
                product?.let { button("[Карточка]", routes.info(it), "Сведения о плагине $it", UpdatePalette.ACCENT) },
                button("[Плагины]", routes.list, "Все зарегистрированные плагины", UpdatePalette.ACCENT),
            )?.let { result += it }
            pager(page, pages, base, "Новее", "Старее")?.let { result += it }
            if (!routes.interactive) {
                result += commandHints(
                    "$base <страница>" to "страница",
                    "${routes.root} history plugin <плагин>" to "события одного плагина",
                    routes.list to "плагины",
                )
            }
            return result
        }

        fun backups(backups: List<UpdateBackup>, page: Int): List<Component> {
            require(page > 0) { "page must be positive" }
            val selected = backups.page(page)
            val result = mutableListOf(
                header("Резервы", text(plural(backups.size, "набор", "набора", "наборов"), UpdatePalette.SOFT)),
                note("Сохранённые версии плагинов для отката."),
                blank(),
            )
            selected.forEach { result += backupRows(it) }
            if (selected.isEmpty()) {
                result += note(if (backups.isEmpty()) "Резервов пока нет: они появятся после обновления." else "На этой странице пусто.")
            }
            result += blank()
            actions(
                button("[Плагины]", routes.list, "Все зарегистрированные плагины", UpdatePalette.ACCENT),
                button("[История]", routes.history, "Кто, когда и что обновлял", UpdatePalette.ACCENT),
            )?.let { result += it }
            pager(page, pageCount(backups.size), routes.backups, "Предыдущая", "Следующая")?.let { result += it }
            return result
        }

        fun info(snapshot: UpdateSnapshot, heldUntil: Instant?): List<Component> {
            val product = snapshot.product
            val update = snapshot.latestVersion?.takeIf { it != snapshot.currentVersion }
            val result = mutableListOf(header(product, text(stateLabel(snapshot.state), stateColor(snapshot.state))))
            snapshot.message?.takeIf { it.isNotBlank() }?.let { result += note(it.take(MAX_MESSAGE)) }
            result += blank()
            var version = field("Версия", versions(snapshot.currentVersion, update))
            snapshot.latestChangelogUrl?.takeIf(::safeLink)?.takeIf { update != null }?.let { version = version.append(changelogButton(it)) }
            result += version
            releasedAt(snapshot, update ?: snapshot.currentVersion)?.let { result += field("Выпущена", text(released(it), UpdatePalette.TEXT)) }
            result += field("Канал", channelTag(snapshot.channel)).hoverEvent(tooltip(text(channelHint(snapshot.channel), UpdatePalette.TEXT)))
                .append(change(product, "Выбрать другой канал обновлений"))
            otherChannels(snapshot)?.let { result += it }
            result += field("Автозагрузка", if (snapshot.automaticDownload) text("включена", UpdatePalette.OK) else text("выключена", UpdatePalette.DANGER))
                .append(change(product, "Включить или выключить автоматическую загрузку"))
            val held = snapshot.state == UpdateState.FROZEN || heldUntil != null
            result += field("Пауза", if (held) text(holdText(heldUntil), UpdatePalette.WARN) else text("нет", UpdatePalette.TEXT))
                .append(change(product, "Приостановить обновления на время или снять паузу"))
            result += requirements(snapshot)
            result += blank()
            actions(
                routes.update?.takeIf { snapshot.state == UpdateState.UPDATE_AVAILABLE }?.let {
                    button("[Обновить]", it(product), "Скачать, проверить и подготовить обновление.\nПрименится после перезапуска.", UpdatePalette.OK)
                },
                button("[Настройки]", routes.settings(product), "Канал, автозагрузка и пауза обновлений", UpdatePalette.ACCENT),
                button("[История]", routes.history(product), "События только этого плагина", UpdatePalette.ACCENT),
                button("[Назад]", routes.list, "К списку плагинов", UpdatePalette.SOFT),
            )?.let { result += it }
            if (!routes.interactive) {
                result += commandHints(
                    routes.update?.takeIf { snapshot.state == UpdateState.UPDATE_AVAILABLE }?.invoke(product) to "обновить",
                    routes.settings(product) to "настройки",
                    routes.history(product) to "история плагина",
                )
            }
            return result
        }

        fun settings(snapshot: UpdateSnapshot, heldUntil: Instant?): List<Component> {
            val product = snapshot.product
            val result = mutableListOf(
                header(product, text("настройки", UpdatePalette.LABEL)),
                text("  ${version(snapshot.currentVersion)}", UpdatePalette.SOFT).append(dot()).append(channelTag(snapshot.channel)),
            )
            if (routes.interactive && !routes.editable) result += note("Только просмотр: нет права менять настройки.")
            if (clickable) result += note("Нажмите на кнопку, чтобы выбрать. ${glyph('✔')} — выбрано сейчас.")
            if (clickable) result += note("Пауза — не дольше 7 дней.")
            result += blank()
            result += choices("Канал", CHANNELS.map { it to channelName(it) }, snapshot.channel, ::channelColor) { channel ->
                routes.set(product, "channel", channelName(channel)) to channelHint(channel)
            }
            result += choices("Автозагрузка", listOf(true to "вкл", false to "выкл"), snapshot.automaticDownload,
                { on -> if (on) UpdatePalette.OK else UpdatePalette.DANGER }) { on ->
                routes.set(product, "auto", if (on) "on" else "off") to
                    if (on) "Скачивать и проверять новые версии автоматически.\nПрименение — после перезапуска." else "Обновлять только по команде администратора"
            }
            result += holdRows(product, snapshot.state == UpdateState.FROZEN || heldUntil != null, heldUntil)
            result += blank()
            actions(
                button("[Карточка]", routes.info(product), "Сведения о плагине", UpdatePalette.ACCENT),
                button("[История]", routes.history(product), "События только этого плагина", UpdatePalette.ACCENT),
                button("[Назад]", routes.list, "К списку плагинов", UpdatePalette.SOFT),
            )?.let { result += it }
            if (!routes.interactive || !routes.editable) {
                result += commandHints(
                    routes.set(product, "channel", "<${CHANNELS.joinToString("|") { channelName(it) }}>") to "канал",
                    routes.set(product, "auto", "<on|off>") to "автозагрузка",
                    routes.set(product, "hold", "<${HOLD_PERIODS.joinToString("|")}|off>") to "пауза",
                )
            }
            return result
        }

        fun notice(notice: UpdateNotice): List<Component> {
            val (mark, color) = when (notice.tone) {
                UpdateTone.PROGRESS -> '»' to UpdatePalette.ACCENT
                UpdateTone.INFO -> '■' to UpdatePalette.INFO
                UpdateTone.SUCCESS -> '√' to UpdatePalette.OK
                UpdateTone.WARNING -> '!' to UpdatePalette.WARN
                UpdateTone.FAILURE -> '!' to UpdatePalette.DANGER
            }
            val text = if (notice.tone == UpdateTone.FAILURE) UpdatePalette.DANGER else UpdatePalette.TEXT
            return listOfNotNull(
                text("  ${glyph(mark)} ", color).append(text(notice.text.take(MAX_MESSAGE), text)),
                notice.detail?.takeIf { it.isNotBlank() }?.let { text("    ${it.take(MAX_MESSAGE)}", UpdatePalette.LABEL) },
            )
        }

        fun confirmation(title: String, lines: List<String>, label: String, command: String, hint: String): List<Component> {
            val result = mutableListOf(header(title), blank())
            lines.forEach { result += note(it, UpdatePalette.TEXT) }
            result += blank()
            if (routes.interactive) result += text("  ").append(button(label, command, hint, UpdatePalette.DANGER))
            else result += text("  Подтвердить: ", UpdatePalette.LABEL).append(text(command, UpdatePalette.LABEL))
            return result
        }

        fun status(facts: List<Pair<String, String>>, snapshots: List<UpdateSnapshot>, support: String?): List<Component> {
            val result = mutableListOf(header("Состояние", if (snapshots.isEmpty()) null else catalogSummary(snapshots)), blank())
            facts.forEach { (label, value) -> result += field(label, text(value, UpdatePalette.TEXT)) }
            result += Component.empty()
            if (snapshots.isEmpty()) result += note("Пока нет плагинов, зарегистрированных в pnLibrary.")
            else snapshots.sortedBy { it.product.lowercase() }.forEach { result += catalogRows(it) }
            result += blank()
            actions(
                button("[Каталог]", routes.list, "Все плагины с настройками и историей", UpdatePalette.ACCENT),
                button("[Проверить]", routes.check, "Запросить новые версии у каталогов", UpdatePalette.OK),
                support?.let(::supportButton),
            )?.let { result += it }
            return result
        }

        fun announcement(snapshots: List<UpdateSnapshot>, support: String?): List<Component> {
            val actionable = snapshots.filter { it.state == UpdateState.UPDATE_AVAILABLE || it.state == UpdateState.UPDATE_STAGED }
                .sortedBy { it.product.lowercase() }
            if (actionable.isEmpty()) return emptyList()
            val result = mutableListOf(header("Обновления pnFolder", catalogSummary(actionable)), blank())
            actionable.forEach { result += catalogRows(it) }
            result += blank()
            actions(
                button("[Обновления]", routes.available, "Доступные и подготовленные обновления", UpdatePalette.ACCENT),
                button("[Проверить]", routes.check, "Повторно проверить все обновления", UpdatePalette.OK),
                support?.let(::supportButton),
            )?.let { result += it }
            return result
        }

        private fun supportButton(link: String): Component =
            if (!routes.interactive || !safeLink(link)) Component.empty()
            else bracketed("Поддержка", UpdatePalette.WARN).clickEvent(ClickEvent.openUrl(link))
                .hoverEvent(tooltip(text("Открыть поддержку pnFolder", UpdatePalette.TEXT), text(link.take(MAX_LINE), UpdatePalette.LABEL)))

        // --- catalog -----------------------------------------------------------------------------------------

        /** Counters beside the title; zero counters are left out and nothing pending reads as all clear. */
        private fun catalogSummary(snapshots: List<UpdateSnapshot>): Component {
            val available = snapshots.count { it.state == UpdateState.UPDATE_AVAILABLE }
            val staged = snapshots.count { it.state == UpdateState.UPDATE_STAGED || it.state == UpdateState.DOWNLOADING }
            val broken = snapshots.count { it.state in problems }
            val chips = mutableListOf(chip(snapshots.size, UpdatePalette.ACCENT, "плагин", "плагина", "плагинов"))
            if (available > 0) chips += chip(available, UpdatePalette.ACCENT, "к обновлению", "к обновлению", "к обновлению")
            if (staged > 0) chips += chip(staged, UpdatePalette.ORANGE, "к перезапуску", "к перезапуску", "к перезапуску")
            if (broken > 0) chips += chip(broken, UpdatePalette.DANGER, "ошибка", "ошибки", "ошибок")
            if (available + staged == 0 && snapshots.isNotEmpty()) chips += text("всё актуально", UpdatePalette.OK)
            return chips.drop(1).fold(chips.first()) { line, chip -> line.append(dot()).append(chip) }
        }

        /** A plugin row: name, versions, channel and state words; the whole row opens the plugin card. */
        private fun catalogRows(snapshot: UpdateSnapshot): List<Component> {
            val update = updateTarget(snapshot)
            val changelog = snapshot.latestChangelogUrl?.takeIf(::safeLink)?.takeIf { snapshot.state in pending }
            var row = text("  ").append(text(snapshot.product, UpdatePalette.TEXT)).append(text(" "))
                .append(versions(snapshot.currentVersion, update, changelog?.takeIf { routes.interactive }))
            if (snapshot.channel != UpdateChannel.STABLE) row = row.append(text("  ")).append(channelTag(snapshot.channel))
            if (snapshot.state != UpdateState.UP_TO_DATE && snapshot.state != UpdateState.UPDATE_AVAILABLE) {
                row = row.append(text("  ")).append(text(shortStateLabel(snapshot.state), stateColor(snapshot.state)))
            }
            routes.update?.takeIf { snapshot.state == UpdateState.UPDATE_AVAILABLE }?.let { command ->
                row = row.append(spaced(button("[Обновить]", command(snapshot.product),
                    "Скачать, проверить и подготовить ${version(update ?: "")}.\nПрименится после перезапуска.", UpdatePalette.OK)))
            }
            row = row.hoverEvent(tooltip(*catalogCard(snapshot, update).toTypedArray()))
            if (routes.interactive) row = row.clickEvent(ClickEvent.runCommand(routes.info(snapshot.product)))
            val rows = mutableListOf(row)
            if (!routes.interactive && changelog != null) rows += text("      изменения: $changelog", UpdatePalette.LABEL)
            return rows
        }

        private fun catalogCard(snapshot: UpdateSnapshot, update: String?): List<Component> = listOfNotNull(
            text(snapshot.product, UpdatePalette.TEXT),
            hoverField("Статус", text(stateLabel(snapshot.state), stateColor(snapshot.state))),
            hoverField("Версия", versions(snapshot.currentVersion, update)),
            releasedAt(snapshot, update ?: snapshot.currentVersion)?.let { hoverField("Выпущена", text(released(it), UpdatePalette.TEXT)) },
            hoverField("Канал", channelTag(snapshot.channel)),
            hoverField("Автозагрузка", text(if (snapshot.automaticDownload) "включена" else "выключена", UpdatePalette.TEXT)),
            snapshot.message?.takeIf { it.isNotBlank() }?.let { text(it.take(MAX_MESSAGE), UpdatePalette.LABEL) },
            blank().takeIf { routes.interactive },
            text("Нажмите, чтобы открыть карточку", UpdatePalette.ACCENT).takeIf { routes.interactive },
        )

        // --- info and settings -------------------------------------------------------------------------------

        /** The newest release of every other channel; clicking one switches to its channel. */
        private fun otherChannels(snapshot: UpdateSnapshot): Component? {
            val newest = CHANNELS.filter { it != snapshot.channel }.mapNotNull { channel ->
                snapshot.availableReleases.filter { it.channel == channel }
                    .maxByOrNull { it.publishedAt ?: Instant.EPOCH }?.let { channel to it }
            }
            if (newest.isEmpty()) return null
            val entries = newest.map { (channel, release) ->
                val entry = channelTag(channel).append(text(" ${version(release.version)}", UpdatePalette.TEXT))
                val card = listOfNotNull(
                    text("${channelName(channel)} ${version(release.version)}", channelColor(channel)),
                    release.publishedAt?.let { hoverField("Выпущена", text(released(it), UpdatePalette.TEXT)) },
                    text(channelHint(channel), UpdatePalette.LABEL),
                    text("» Нажмите, чтобы переключиться на ${channelName(channel)}", UpdatePalette.LABEL).takeIf { clickable },
                )
                val shown = entry.hoverEvent(tooltip(*card.toTypedArray()))
                if (clickable) shown.clickEvent(ClickEvent.runCommand(routes.set(snapshot.product, "channel", channelName(channel)))) else shown
            }
            return field("Другие каналы", entries.drop(1).fold(entries.first()) { line, entry -> line.append(dot()).append(entry) })
        }

        /** Java and API requirements; a Java version the server does not meet is shown in red with the actual one. */
        private fun requirements(snapshot: UpdateSnapshot): Component {
            val javaMet = snapshot.currentJava >= snapshot.requiredJava
            var value = text("Java ${snapshot.requiredJava}+", if (javaMet) UpdatePalette.TEXT else UpdatePalette.DANGER)
            if (!javaMet) value = value.append(text(" (на сервере ${snapshot.currentJava})", UpdatePalette.DANGER))
            snapshot.supportedApi?.let { value = value.append(dot()).append(text("API ${it.minimum}–${it.maximum}", UpdatePalette.TEXT)) }
            return field("Требует", value).hoverEvent(tooltip(text("На сервере Java ${snapshot.currentJava}", UpdatePalette.TEXT)))
        }

        /** The current pause with a button to lift it, and the periods to pause for or to replace the pause with. */
        private fun holdRows(product: String, held: Boolean, heldUntil: Instant?): List<Component> {
            if (!clickable) return listOf(setting("Пауза", if (held) text(holdText(heldUntil), UpdatePalette.WARN) else selected("нет", UpdatePalette.OK)))
            if (!held) return listOf(setting("Пауза", selected("нет", UpdatePalette.OK).append(text(" ")).append(holdPeriods(product, replacing = false))))
            return listOf(
                setting("Пауза", selected(holdText(heldUntil), UpdatePalette.WARN)).append(text(" "))
                    .append(button("[Снять]", routes.set(product, "hold", "off"), "Снова предлагать и загружать обновления", UpdatePalette.OK)),
                setting("Продлить", holdPeriods(product, replacing = true)),
            )
        }

        private fun holdPeriods(product: String, replacing: Boolean): Component = HOLD_PERIODS.mapIndexed { index, period ->
            val hint = if (replacing) "Пауза на ${periodLabel(period)} с этого момента" else "Приостановить обновления на ${periodLabel(period)}"
            text(if (index == 0) "" else " ")
                .append(option(periodLabel(period), routes.set(product, "hold", period), "$hint.\nНовые версии не предлагаются и не загружаются."))
        }.fold(Component.empty()) { line, period -> line.append(period) }

        private fun holdText(until: Instant?): String =
            if (until == null) "задана в updates.yml" else "до ${fullTime.format(until.atZone(zone))} · ${remaining(Duration.between(now, until))}"

        private fun remaining(left: Duration): String = when {
            left.toMinutes() < 60 -> "ещё ${maxOf(1, left.toMinutes())} мин"
            left.toHours() < 48 -> "ещё ${left.toHours()} ч"
            else -> "ещё ${left.toDays()} д"
        }

        private fun periodLabel(period: String): String = period.dropLast(1) + when (period.last()) {
            'h' -> " ч"
            'd' -> " д"
            else -> " мин"
        }

        /** A small button that opens the settings card, shown after the setting it changes. */
        private fun change(product: String, hint: String): Component =
            if (!clickable) Component.empty() else text(" ").append(button("[изменить]", routes.settings(product), hint, UpdatePalette.ACCENT))

        /**
         * A row of mutually exclusive choices drawn as buttons, each in its own color: the current one is `[✔ name]` in
         * its color, the others have gray brackets and switch to that choice on click.
         */
        private fun <T> choices(
            label: String,
            options: List<Pair<T, String>>,
            current: T,
            color: (T) -> TextColor,
            command: (T) -> Pair<String, String>,
        ): Component {
            val shown = options.map { (value, name) ->
                when {
                    value == current -> selected(name, color(value))
                    clickable -> {
                        val (run, hint) = command(value)
                        bracketed(name, color(value)).clickEvent(ClickEvent.runCommand(run))
                            .hoverEvent(tooltip(text("Выбрать: $name", color(value)), text(hint, UpdatePalette.TEXT), blank(), text(run, UpdatePalette.LABEL)))
                    }
                    else -> text(name, UpdatePalette.FAINT)
                }
            }
            return setting(label, shown.drop(1).fold(shown.first()) { line, option -> line.append(text(" ")).append(option) })
        }

        // --- history and backups -----------------------------------------------------------------------------

        private fun historyRows(entry: UpdateHistoryEntry, offerFilter: Boolean): List<Component> {
            val outcome = outcome(entry)
            val lead = text("    ")
            val tail = text("  ").append(text(outcome.label, outcome.color))
            if (entry.changes.isEmpty()) {
                val body = entry.message?.takeIf { it.isNotBlank() }?.take(MAX_LINE) ?: operationLabel(entry.operation)
                return listOf(lead.append(text(body, UpdatePalette.TEXT)).append(tail).hoverEvent(historyCard(entry, outcome, null, false)))
            }
            return entry.changes.map { change ->
                val product = change.product.toString()
                val transition = when (entry.operation) {
                    "ROLLBACK", "HEALTH_ROLLBACK" -> text("откат к ${change.from?.let { version(it.toString()) } ?: "не установлено"}", UpdatePalette.SOFT)
                    ComponentLedger.OPERATION_ADDED -> text(version(change.to.toString()), UpdatePalette.SOFT)
                    ComponentLedger.OPERATION_REMOVED, ComponentLedger.OPERATION_SETTINGS_CHANGED ->
                        text(version((change.from ?: change.to).toString()), UpdatePalette.SOFT)
                    else -> versions(change.from?.toString() ?: "-", change.to.toString())
                }
                var row = lead.append(text("$product  ", UpdatePalette.TEXT)).append(transition).append(tail)
                    .hoverEvent(historyCard(entry, outcome, product, offerFilter))
                if (offerFilter && routes.interactive) row = row.clickEvent(ClickEvent.runCommand(routes.history(product)))
                row
            }
        }

        private fun historyCard(entry: UpdateHistoryEntry, outcome: Outcome, product: String?, offerFilter: Boolean): HoverEvent<Component> {
            val at = entry.occurredAt.atZone(zone)
            return tooltip(*listOfNotNull(
                product?.let { text(it, UpdatePalette.TEXT) },
                hoverField("Событие", text(operationLabel(entry.operation), UpdatePalette.TEXT)),
                hoverField("Итог", text(outcome.detail, outcome.color)),
                hoverField("Кто", text(entry.actor.name, UpdatePalette.TEXT)),
                hoverField("Когда", text("${fullTime.format(at)} · ${entry.occurredAt.atOffset(ZoneOffset.UTC)}", UpdatePalette.TEXT)),
                hoverField("План", text(entry.planId.toString(), UpdatePalette.LABEL)),
                entry.message?.takeIf { it.isNotBlank() }?.let { text(it.take(MAX_MESSAGE), UpdatePalette.SOFT) },
                product?.takeIf { offerFilter }?.let { blank() },
                product?.takeIf { offerFilter }?.let { text("Показать только $it", UpdatePalette.ACCENT) },
                product?.takeIf { offerFilter }?.let { text(routes.history(it), UpdatePalette.LABEL) },
            ).toTypedArray())
        }

        private fun outcome(entry: UpdateHistoryEntry): Outcome = when {
            entry.operation == ComponentLedger.OPERATION_ADDED -> Outcome("установлен", UpdatePalette.OK)
            entry.operation == ComponentLedger.OPERATION_REMOVED -> Outcome("удалён", UpdatePalette.DANGER)
            entry.operation == ComponentLedger.OPERATION_VERSION_CHANGED -> Outcome("изменена вручную", UpdatePalette.ORANGE, "версия изменена вручную")
            entry.operation == ComponentLedger.OPERATION_SETTINGS_CHANGED ->
                Outcome(entry.message?.takeIf { it.isNotBlank() }?.take(MAX_LINE) ?: "настройки изменены", UpdatePalette.WARN)
            entry.state == UpdateState.FAILED -> Outcome("ошибка", UpdatePalette.DANGER)
            entry.operation == "HEALTH" && entry.state == UpdateState.UP_TO_DATE -> Outcome("применено", UpdatePalette.OK, "применено после перезапуска")
            entry.operation == "HEALTH_ROLLBACK" && entry.state == UpdateState.ROLLED_BACK ->
                Outcome("подтверждён", UpdatePalette.OK, "откат подтверждён после перезапуска")
            entry.operation == "ROLLBACK" || entry.state == UpdateState.ROLLED_BACK ->
                Outcome("ждёт перезапуска", UpdatePalette.ORANGE, "откат подготовлен, применится после перезапуска")
            entry.state == UpdateState.UPDATE_STAGED -> Outcome("подготовлено", UpdatePalette.ORANGE, "подготовлено, применится после перезапуска")
            entry.state == UpdateState.UPDATE_AVAILABLE -> Outcome("доступна новая", UpdatePalette.INFO, "доступна новая версия")
            else -> Outcome(shortStateLabel(entry.state), stateColor(entry.state), stateLabel(entry.state))
        }

        private fun backupRows(backup: UpdateBackup): List<Component> {
            val restores = backup.components.joinToString { "${it.product} ${it.backupVersion?.let(::version) ?: "?"}" }
            val card = listOfNotNull(
                text("Резервный набор", UpdatePalette.TEXT),
                hoverField("ID", text(backup.id.toString(), UpdatePalette.LABEL)),
                hoverField("Создан", text(backup.createdAt?.let { "${fullTime.format(it.atZone(zone))} · ${age(it)}" } ?: "дата не записана", UpdatePalette.TEXT)),
            ) + backup.components.map { component ->
                hoverField("Вернёт", text("${component.product} ${component.backupVersion?.let(::version) ?: "?"}", UpdatePalette.TEXT)
                    .append(text(" (сейчас ${component.updatedVersion?.let(::version) ?: "?"})", UpdatePalette.SOFT)))
            } + hoverField("Конфигураций", text("${backup.configurationFiles}", UpdatePalette.TEXT))
            var row = text("  ").append(text(restores, UpdatePalette.TEXT))
                .append(text("  ")).append(text(backup.createdAt?.let(::age) ?: "дата не записана", UpdatePalette.SOFT))
                .hoverEvent(tooltip(*card.toTypedArray()))
                .append(spaced(button("[Откат]", routes.rollback(backup.id), "Открыть подтверждение отката.\nКонфигурации не меняются.", UpdatePalette.WARN)))
            if (backup.configurationFiles > 0) {
                row = row.append(spaced(button("[+конфиги]", routes.rollback(backup.id, true),
                    "Открыть подтверждение отката JAR\nи выбранных конфигураций.", UpdatePalette.ORANGE)))
            }
            val rows = mutableListOf(row)
            if (!routes.interactive) {
                rows += text("      откат: ${routes.rollback(backup.id)}", UpdatePalette.LABEL)
                if (backup.configurationFiles > 0) rows += text("      с конфигами: ${routes.rollback(backup.id, true)}", UpdatePalette.LABEL)
            }
            return rows
        }

        // --- building blocks ---------------------------------------------------------------------------------

        /** The first line of every page: the title in bold accent, then its details after two spaces. */
        private fun header(title: String, details: Component? = null): Component {
            val name = Component.text(shown(title), UpdatePalette.ACCENT).decoration(TextDecoration.BOLD, true)
            return if (details == null) name else Component.empty().append(name).append(text("  ")).append(details)
        }

        /** Sections are separated by an empty line, never by rules or frames. */
        private fun blank(): Component = Component.empty()


        private fun field(label: String, value: Component): Component = text("  $label: ", UpdatePalette.LABEL).append(value)

        private fun hoverField(label: String, value: Component): Component = text("$label: ", UpdatePalette.LABEL).append(value)

        private fun chip(count: Int, color: TextColor, one: String, few: String, many: String): Component =
            text("$count", color).append(text(" ${pluralWord(count, one, few, many)}", UpdatePalette.LABEL))

        /** A separator between parts of a line. */
        private fun dot(): Component = text(" · ", UpdatePalette.SOFT)

        private fun channelTag(channel: UpdateChannel): Component = text(channelName(channel), channelColor(channel))

        /**
         * The current version, then an arrow and the highlighted new one when there is an update. With [changelog]
         * the new version opens the list of changes.
         */
        private fun versions(current: String, update: String?, changelog: String? = null): Component {
            val base = text(version(current), UpdatePalette.SOFT)
            if (update == null) return base
            var next = text(version(update), UpdatePalette.INFO)
            if (changelog != null) {
                next = next.clickEvent(ClickEvent.openUrl(changelog))
                    .hoverEvent(tooltip(text("Список изменений ${version(update)}", UpdatePalette.TEXT), text(changelog.take(MAX_LINE), UpdatePalette.LABEL)))
            }
            return base.append(text(" ${glyph('→')} ", UpdatePalette.SOFT)).append(next)
        }

        /** Versions that start with a digit read as `v1.2.0`; anything else is shown as published. */
        private fun version(value: String): String = if (value.firstOrNull()?.isDigit() == true) "v$value" else value

        private fun updateTarget(snapshot: UpdateSnapshot): String? =
            snapshot.latestVersion?.takeIf { it != snapshot.currentVersion && snapshot.state in pending }

        private fun releasedAt(snapshot: UpdateSnapshot, version: String): Instant? =
            snapshot.availableReleases.firstOrNull { it.version == version }?.publishedAt

        private fun released(at: Instant): String {
            val day = at.atZone(zone).toLocalDate()
            val date = if (day.year == now.atZone(zone).year) dayFormat.format(day) else dayYearFormat.format(day)
            return if (Duration.between(at, now).toDays() < AGE_DAYS) "$date · ${age(at)}" else date
        }

        /** Buttons separated by spaces, or nothing when the reader cannot click any of them. */
        private fun actions(vararg buttons: Component?): Component? {
            val visible = buttons.filterNotNull().filter { it != Component.empty() }
            if (visible.isEmpty()) return null
            return visible.drop(1).fold(text("  ").append(visible.first())) { line, button -> line.append(text(" ")).append(button) }
        }

        /**
         * A clickable `[label]` whose brackets are gray and whose label carries [color], or nothing when the reader
         * cannot click; [commandHints] lists the commands instead.
         */
        private fun button(label: String, command: String, hint: String, color: TextColor): Component =
            if (!routes.interactive) Component.empty()
            else bracketed(label.removePrefix("[").removeSuffix("]"), color).clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(tooltip(text(hint, UpdatePalette.TEXT), blank(), text(command, UpdatePalette.LABEL)))

        private fun bracketed(label: String, color: TextColor): Component =
            text("[", UpdatePalette.SOFT).append(text(label, color)).append(text("]", UpdatePalette.SOFT))

        /** The current choice of a settings row: marked with a check and in its own color, so it reads as already pressed. */
        private fun selected(label: String, color: TextColor): Component =
            Component.text(shown("[${glyph('✔')} $label]"), color)
                .hoverEvent(tooltip(text("Выбрано сейчас", color)))

        /** A settings row: the label in white, so the row reads as a control rather than a gray caption. */
        private fun setting(label: String, value: Component): Component = text("  $label: ", UpdatePalette.TEXT).append(value)

        /** A bracketed choice in the warning color, like the pause periods of the settings card. */
        private fun option(label: String, command: String, hint: String): Component =
            if (!routes.interactive) Component.empty()
            else bracketed(label, UpdatePalette.WARN).clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(tooltip(text(hint, UpdatePalette.TEXT), blank(), text(command, UpdatePalette.LABEL)))

        /** [button] led by a space, or nothing when the button is hidden. */
        private fun spaced(button: Component): Component = if (button == Component.empty()) button else text(" ").append(button)

        private fun changelogButton(link: String): Component =
            if (!routes.interactive) text(" $link", UpdatePalette.LABEL)
            else text(" ").append(bracketed("Изменения", UpdatePalette.INFO)).clickEvent(ClickEvent.openUrl(link))
                .hoverEvent(tooltip(text("Открыть список изменений", UpdatePalette.TEXT), text(link.take(MAX_LINE), UpdatePalette.LABEL)))

        /** Previous and next page buttons around the page number; nothing for a single page. */
        private fun pager(page: Int, totalPages: Int, base: String, previous: String, next: String): Component? {
            if (totalPages <= 1) return null
            var line = text("  ")
            line = line.append(text("страница ", UpdatePalette.LABEL)).append(text("$page", UpdatePalette.ACCENT))
                .append(text(" из ", UpdatePalette.LABEL)).append(text("$totalPages", UpdatePalette.ACCENT))
            if (page > 1) line = line.append(text(" ")).append(button("[$previous]", "$base ${page - 1}", "Страница ${page - 1}", UpdatePalette.ACCENT))
            if (page < totalPages) line = line.append(text(" ")).append(button("[$next]", "$base ${page + 1}", "Страница ${page + 1}", UpdatePalette.ACCENT))
            if (!routes.interactive && page < totalPages) line = line.append(text(" (следующая: $base ${page + 1})", UpdatePalette.LABEL))
            return line
        }

        /** The footer of a reader that cannot click: one command per line with what it does. */
        private fun commandHints(vararg hints: Pair<String?, String>): List<Component> =
            listOf(text("  Команды:", UpdatePalette.LABEL)) + hints.mapNotNull { (command, purpose) ->
                command?.let { text("    $it", UpdatePalette.LABEL).append(dot()).append(text(purpose, UpdatePalette.LABEL)) }
            }

        private fun note(value: String, color: TextColor = UpdatePalette.LABEL): Component = text(" $value", color)

        /** Lines of a hover card joined with line breaks. */
        private fun tooltip(vararg lines: Component): HoverEvent<Component> =
            HoverEvent.showText(lines.drop(1).fold(lines.first()) { card, line -> card.append(Component.newline()).append(line) })

        private fun text(value: String, color: TextColor? = null): Component {
            val component = Component.text(shown(value))
            return if (color == null) component else component.color(color)
        }

        private fun shown(value: String): String = if (routes.ascii) asciiOnly(value) else value

        private fun glyph(symbol: Char): String = shown(symbol.toString())

        /** ASCII stand-ins for the catalog's glyphs; any other character outside ASCII and Cyrillic becomes `?`. */
        private fun asciiOnly(value: String): String = buildString {
            value.forEach { char ->
                when {
                    char.code < 128 || char in 'А'..'я' || char == 'ё' || char == 'Ё' -> append(char)
                    else -> append(ASCII[char] ?: "?")
                }
            }
        }

        private fun <T> List<T>.page(page: Int): List<T> {
            val offset = (page.toLong() - 1) * PAGE_SIZE
            return if (offset >= size) emptyList() else drop(offset.toInt()).take(PAGE_SIZE)
        }

        private fun pageCount(total: Int): Int = maxOf(1, (total + PAGE_SIZE - 1) / PAGE_SIZE)

        private fun dayLabel(day: LocalDate): String {
            val today = now.atZone(zone).toLocalDate()
            return when {
                day == today -> "Сегодня"
                day == today.minusDays(1) -> "Вчера"
                day.year == today.year -> dayFormat.format(day)
                else -> dayYearFormat.format(day)
            }
        }

        private fun age(at: Instant): String {
            val elapsed = Duration.between(at, now)
            return when {
                elapsed.isNegative || elapsed.toMinutes() < 1 -> "только что"
                elapsed.toMinutes() < 60 -> "${elapsed.toMinutes()} мин назад"
                elapsed.toHours() < 24 -> "${elapsed.toHours()} ч назад"
                elapsed.toDays() < AGE_DAYS -> "${elapsed.toDays()} д назад"
                else -> dayYearFormat.format(at.atZone(zone))
            }
        }

        private fun plural(count: Int, one: String, few: String, many: String): String = "$count ${pluralWord(count, one, few, many)}"

        private fun pluralWord(count: Int, one: String, few: String, many: String): String = when {
            count % 100 in 11..14 -> many
            count % 10 == 1 -> one
            count % 10 in 2..4 -> few
            else -> many
        }

        private fun safeLink(value: String): Boolean = runCatching {
            val uri = URI(value)
            value.length <= 4096 && uri.scheme.equals("https", true) && uri.host != null && uri.userInfo == null
        }.getOrDefault(false)

        private fun channelName(channel: UpdateChannel): String = channel.name.lowercase(Locale.ROOT)

        private fun channelColor(channel: UpdateChannel): TextColor = when (channel) {
            UpdateChannel.STABLE -> UpdatePalette.OK
            UpdateChannel.RC -> UpdatePalette.INFO
            UpdateChannel.BETA -> UpdatePalette.WARN
            UpdateChannel.ALPHA -> UpdatePalette.ORANGE
            UpdateChannel.DEV -> UpdatePalette.DANGER
        }

        private fun channelHint(channel: UpdateChannel): String = when (channel) {
            UpdateChannel.STABLE -> "Только стабильные релизы"
            UpdateChannel.RC -> "Кандидаты в релиз и стабильные"
            UpdateChannel.BETA -> "Бета-версии и стабильные"
            UpdateChannel.ALPHA -> "Экспериментальные alpha-версии: возможны ошибки"
            UpdateChannel.DEV -> "Все сборки, включая разрабатываемые: только для тестового сервера"
        }

        private fun stateColor(state: UpdateState): TextColor = when (state) {
            UpdateState.UP_TO_DATE -> UpdatePalette.OK
            UpdateState.UPDATE_AVAILABLE -> UpdatePalette.INFO
            UpdateState.UPDATE_STAGED, UpdateState.DOWNLOADING, UpdateState.ROLLED_BACK -> UpdatePalette.ORANGE
            UpdateState.FAILED, UpdateState.BLOCKED, UpdateState.INCOMPATIBLE -> UpdatePalette.DANGER
            UpdateState.FROZEN -> UpdatePalette.WARN
            UpdateState.CHECKING -> UpdatePalette.LABEL
        }


        private fun stateLabel(state: UpdateState): String = when (state) {
            UpdateState.UP_TO_DATE -> "актуально"
            UpdateState.UPDATE_AVAILABLE -> "доступно обновление"
            UpdateState.UPDATE_STAGED -> "подготовлено к перезапуску"
            UpdateState.FROZEN -> "приостановлено"
            UpdateState.INCOMPATIBLE -> "несовместимо"
            UpdateState.BLOCKED -> "заблокировано"
            UpdateState.CHECKING -> "проверка"
            UpdateState.DOWNLOADING -> "загрузка"
            UpdateState.FAILED -> "ошибка"
            UpdateState.ROLLED_BACK -> "откат подготовлен к перезапуску"
        }

        /** [stateLabel] short enough for a catalog row; the full wording stays in the hover card. */
        private fun shortStateLabel(state: UpdateState): String = when (state) {
            UpdateState.UPDATE_STAGED -> "ждёт перезапуска"
            UpdateState.FROZEN -> "пауза"
            UpdateState.ROLLED_BACK -> "откат ждёт перезапуска"
            else -> stateLabel(state)
        }

        private fun operationLabel(operation: String): String = when (operation) {
            "CHECK" -> "проверка"
            "STAGE" -> "обновление"
            "ROLLBACK" -> "откат"
            "HEALTH" -> "проверка запуска"
            "HEALTH_ROLLBACK" -> "проверка отката"
            ComponentLedger.OPERATION_ADDED -> "установка"
            ComponentLedger.OPERATION_REMOVED -> "удаление"
            ComponentLedger.OPERATION_VERSION_CHANGED -> "смена версии"
            ComponentLedger.OPERATION_SETTINGS_CHANGED -> "настройки"
            else -> "состояние"
        }
    }

    private const val MAX_MESSAGE = 512
    private const val MAX_LINE = 120
    private const val AGE_DAYS = 60L

    /** ASCII stand-ins for the console, see `asciiOnly`. */
    private val ASCII: Map<Char, String> = mapOf(
        '─' to "-", '■' to "*", '✔' to "+", '□' to "o", '›' to ">", '✦' to "*", '·' to "|", '»' to "->", '«' to "<-", '√' to "+",
        '→' to "->", '—' to "-", '–' to "-", '…' to "...",
    )
}
