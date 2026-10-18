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
 * Pages follow one layout: a gradient title with counters, lines led by a colored status dot with inline
 * buttons, a gradient rule and a footer with actions and pagination. A reader that cannot click, such as the console or a legacy server
 * without the Adventure transport, gets the same lines without buttons and a list of the commands instead.
 * Nothing here calls the server or changes updates, and stored text is only ever rendered as plain text,
 * never interpreted as markup.
 */
object UpdateCatalogView {
    /** Entries per page, shared with the history reader. */
    const val PAGE_SIZE = 10

    /** Channels a component can be switched between, in order of increasing risk. */
    val CHANNELS: List<UpdateChannel> = UpdateChannel.values().toList()

    private val pending = setOf(UpdateState.UPDATE_AVAILABLE, UpdateState.UPDATE_STAGED, UpdateState.DOWNLOADING)
    private val russian = Locale("ru")
    private val dayFormat = DateTimeFormatter.ofPattern("d MMMM", russian)
    private val dayYearFormat = DateTimeFormatter.ofPattern("d MMMM uuuu", russian)
    private val clock = DateTimeFormatter.ofPattern("HH:mm")
    private val fullTime = DateTimeFormatter.ofPattern("d MMM HH:mm", russian)

    /** Renders one page of registered components, never the native plugin inventory. */
    fun catalog(snapshots: List<UpdateSnapshot>, page: Int, updatesOnly: Boolean, routes: UpdateCatalogRoutes): List<Component> =
        Renderer(routes).catalog(snapshots, page, updatesOnly)

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
    ): List<Component> = Renderer(routes).history(entries, page, totalPages, product, now, zone)

    /** Renders retained rollback sets with their age; paths and file contents are never shown. */
    fun backups(
        backups: List<UpdateBackup>,
        page: Int,
        routes: UpdateCatalogRoutes,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Component> = Renderer(routes).backups(backups, page, now, zone)

    /**
     * Renders the details card of one component: versions, the newest release of every channel and its pause.
     *
     * @param heldUntil when the current pause ends, from [ru.privatenull.pnlibrary.api.updates.UpdateService.heldUntil]
     */
    fun info(
        snapshot: UpdateSnapshot,
        routes: UpdateCatalogRoutes,
        heldUntil: Instant? = null,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Component> = Renderer(routes).info(snapshot, heldUntil, now, zone)

    /** Renders the settings card of one component: channel, automatic download and a pause for a chosen period. */
    fun settings(
        snapshot: UpdateSnapshot,
        routes: UpdateCatalogRoutes,
        heldUntil: Instant? = null,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Component> = Renderer(routes).settings(snapshot, heldUntil, now, zone)

    /** Pause periods offered on the settings card, as accepted by `set <plugin> hold`. */
    val HOLD_PERIODS: List<String> = listOf("1h", "1d", "3d", "7d", "30d")

    private class Renderer(private val routes: UpdateCatalogRoutes) {
        // --- pages -------------------------------------------------------------------------------------------

        fun catalog(snapshots: List<UpdateSnapshot>, page: Int, updatesOnly: Boolean): List<Component> {
            require(page > 0) { "page must be positive" }
            val selected = snapshots.filter { !updatesOnly || it.state in pending }.sortedBy { it.product.lowercase() }
            val entries = selected.page(page)
            val available = selected.count { it.state == UpdateState.UPDATE_AVAILABLE }
            val library = snapshots.firstOrNull { it.product.equals("pnlibrary", ignoreCase = true) }
            val details = buildString {
                append(plural(selected.size, "плагин", "плагина", "плагинов")).append(" · ").append(available).append(" к обновлению")
                library?.let { append(" · pnLibrary: ").append(channelName(it.channel)) }
            }
            val result = mutableListOf(title(if (updatesOnly) "Обновления" else "Каталог", details))
            entries.forEach { result += catalogLine(it) }
            if (entries.isEmpty()) result += muted(if (selected.isEmpty()) "Нет компонентов." else "Нет компонентов на этой странице.")
            result += rule()
            result += actions(
                button("[Проверить]", routes.check, "Проверить новые версии", UpdatePalette.OK),
                button(if (updatesOnly) "[Все]" else "[Обновления]", if (updatesOnly) routes.list else routes.available,
                    if (updatesOnly) "Все зарегистрированные компоненты" else "Только доступные и подготовленные обновления",
                    UpdatePalette.ACCENT),
                button("[История]", routes.history, "Кто, когда и что обновлял", UpdatePalette.ACCENT),
                button("[Резервы]", routes.backups, "Сохранённые версии для отката", UpdatePalette.ACCENT),
            )
            pager(page, pageCount(selected.size), if (updatesOnly) routes.available else routes.list)?.let { result += it }
            if (!routes.interactive) {
                result += commandHints(
                    routes.check to "проверить обновления",
                    routes.available to "только обновления",
                    "${routes.root} info <плагин>" to "сведения",
                    "${routes.root} settings <плагин>" to "настройки",
                    routes.history to "история",
                    routes.backups to "резервы",
                    routes.update?.invoke("<плагин>") to "обновить",
                )
            }
            return result
        }

        fun history(
            entries: List<UpdateHistoryEntry>,
            page: Int,
            totalPages: Int?,
            product: String?,
            now: Instant,
            zone: ZoneId,
        ): List<Component> {
            require(page > 0) { "page must be positive" }
            val result = mutableListOf(title("История", product ?: ""))
            var day: LocalDate? = null
            entries.forEach { entry ->
                val entryDay = entry.occurredAt.atZone(zone).toLocalDate()
                if (entryDay != day) {
                    day = entryDay
                    result += blank()
                    result += text(" ${dayLabel(entryDay, now.atZone(zone).toLocalDate())}", UpdatePalette.ACCENT, TextDecoration.ITALIC)
                }
                result += historyLines(entry, product == null, zone)
            }
            if (entries.isEmpty()) result += muted("Нет событий на этой странице.")
            result += rule()
            val base = if (product == null) routes.history else routes.history(product)
            val pages = totalPages ?: if (entries.size >= PAGE_SIZE) page + 1 else page
            result += historyNavigation(page, pages, base)
            if (!routes.interactive) {
                result += commandHints(
                    "$base <страница>" to "страница",
                    "${routes.root} history plugin <плагин>" to "события одного плагина",
                    routes.list to "плагины",
                )
            }
            return result
        }

        fun backups(backups: List<UpdateBackup>, page: Int, now: Instant, zone: ZoneId): List<Component> {
            require(page > 0) { "page must be positive" }
            val selected = backups.page(page)
            val result = mutableListOf(title("Резервы", plural(backups.size, "набор", "набора", "наборов")))
            selected.forEach { backup ->
                val age = backup.createdAt?.let { age(it, now, zone) } ?: "дата не записана"
                var line = text("  ")
                    .append(text(backup.components.joinToString { component -> "${component.product} ${component.backupVersion ?: "?"}" }, UpdatePalette.TEXT))
                    .append(text(" $age", UpdatePalette.MUTED))
                    .hoverEvent(HoverEvent.showText(text(
                        "Набор: ${backup.id}\nОбновлено до: ${backup.components.joinToString { "${it.product} ${it.updatedVersion ?: "?"}" }}" +
                            "\nКонфигураций: ${backup.configurationFiles}")))
                line = line.append(button(" [Откат]", routes.rollback(backup.id), "Открыть подтверждение отката без изменения конфигураций", UpdatePalette.WARN))
                if (backup.configurationFiles > 0) {
                    line = line.append(button(" [+Конфиг]", routes.rollback(backup.id, true),
                        "Открыть подтверждение отката JAR и выбранных конфигураций", UpdatePalette.ORANGE))
                }
                result += line
                if (!routes.interactive) {
                    result += text("    откат: ${routes.rollback(backup.id)}", UpdatePalette.DIM)
                }
            }
            if (selected.isEmpty()) result += muted(if (backups.isEmpty()) "Нет сохранённых наборов." else "Нет наборов на этой странице.")
            result += rule()
            result += actions(
                button("[Плагины]", routes.list, "Все зарегистрированные компоненты", UpdatePalette.ACCENT),
                button("[История]", routes.history, "Кто, когда и что обновлял", UpdatePalette.ACCENT),
            )
            pager(page, pageCount(backups.size), routes.backups)?.let { result += it }
            return result
        }

        fun info(snapshot: UpdateSnapshot, heldUntil: Instant?, now: Instant, zone: ZoneId): List<Component> {
            val product = snapshot.product
            val result = mutableListOf(title(product, stateLabel(snapshot.state), stateColor(snapshot.state)))
            snapshot.message?.takeIf { it.isNotBlank() }?.let { result += muted(it.take(MAX_MESSAGE)) }
            result += blank()
            result += field("Версия", snapshot.currentVersion)
            snapshot.latestVersion?.takeIf { it != snapshot.currentVersion }?.let { latest ->
                var line = field("Доступна", latest, UpdatePalette.OK)
                snapshot.latestChangelogUrl?.takeIf(::safeLink)?.let { link -> line = line.append(changelogButton(link)) }
                result += line
            }
            result += field("Канал", channelName(snapshot.channel), channelColor(snapshot.channel))
            releasesByChannel(snapshot)?.let { result += it }
            result += field("Автозагрузка", if (snapshot.automaticDownload) "включена" else "выключена",
                if (snapshot.automaticDownload) UpdatePalette.OK else UpdatePalette.MUTED)
            if (snapshot.state == UpdateState.FROZEN || heldUntil != null) {
                result += field("Пауза", holdText(heldUntil, now, zone), UpdatePalette.WARN)
            }
            snapshot.supportedApi?.let { result += field("API", "${it.minimum}-${it.maximum}") }
            result += field("Java", "${snapshot.currentJava} (нужна ${snapshot.requiredJava}+)",
                if (snapshot.currentJava >= snapshot.requiredJava) UpdatePalette.TEXT else UpdatePalette.DANGER)
            result += rule()
            val buttons = mutableListOf<Component>()
            routes.update?.takeIf { snapshot.state == UpdateState.UPDATE_AVAILABLE }?.let { update ->
                buttons += button("[Обновить]", update(product), "Проверить и подготовить обновление. Применение — после перезапуска.", UpdatePalette.OK)
            }
            buttons += button("[Настройки]", routes.settings(product), "Канал, автозагрузка и пауза обновлений", UpdatePalette.ACCENT)
            buttons += button("[История]", routes.history(product), "События только этого плагина", UpdatePalette.ACCENT)
            buttons += button("[Назад]", routes.list, "К списку плагинов", UpdatePalette.MUTED)
            result += actions(*buttons.toTypedArray())
            if (!routes.interactive) {
                result += commandHints(
                    routes.settings(product) to "настройки",
                    routes.history(product) to "история плагина",
                    routes.update?.invoke(product) to "обновить",
                )
            }
            return result
        }

        fun settings(snapshot: UpdateSnapshot, heldUntil: Instant?, now: Instant, zone: ZoneId): List<Component> {
            val product = snapshot.product
            val result = mutableListOf(
                title(product, "настройки"),
                text("  ${snapshot.currentVersion} · ", UpdatePalette.MUTED).append(text(channelName(snapshot.channel), channelColor(snapshot.channel))),
                blank(),
                choices("Канал", CHANNELS.map { it to channelName(it) }, snapshot.channel, ::channelColor) { channel ->
                    routes.set(product, "channel", channelName(channel)) to channelHint(channel)
                },
                choices("Автозагрузка", listOf(true to "вкл", false to "выкл"), snapshot.automaticDownload,
                    { on -> if (on) UpdatePalette.OK else UpdatePalette.MUTED }) { on ->
                    routes.set(product, "auto", if (on) "on" else "off") to
                        if (on) "Скачивать и проверять новые версии автоматически; применение — после перезапуска" else "Только по команде администратора"
                },
                holdLine(product, snapshot.state == UpdateState.FROZEN || heldUntil != null, heldUntil, now, zone),
                rule(),
            )
            result += actions(
                button("[Сведения]", routes.info(product), "Карточка плагина", UpdatePalette.ACCENT),
                button("[История]", routes.history(product), "События только этого плагина", UpdatePalette.ACCENT),
                button("[Назад]", routes.list, "К списку плагинов", UpdatePalette.MUTED),
            )
            if (!routes.interactive || !routes.editable) {
                result += commandHints(
                    routes.set(product, "channel", "<${CHANNELS.joinToString("|") { channelName(it) }}>") to "канал",
                    routes.set(product, "auto", "<on|off>") to "автозагрузка",
                    routes.set(product, "hold", "<${HOLD_PERIODS.joinToString("|")}|off>") to "пауза",
                )
            }
            return result
        }

        /** The newest known release of every channel; clicking one switches to its channel. */
        private fun releasesByChannel(snapshot: UpdateSnapshot): Component? {
            val newest = CHANNELS.mapNotNull { channel ->
                snapshot.availableReleases.filter { it.channel == channel }
                    .maxByOrNull { it.publishedAt ?: Instant.EPOCH }?.let { channel to it.version }
            }
            if (newest.isEmpty()) return null
            return newest.fold(text("  Релизы: ", UpdatePalette.MUTED)) { line, (channel, version) ->
                val entry = text("${channelName(channel)} ", channelColor(channel)).append(text(version, UpdatePalette.TEXT))
                val shown = if (channel != snapshot.channel && routes.interactive && routes.editable) {
                    entry.clickEvent(ClickEvent.runCommand(routes.set(snapshot.product, "channel", channelName(channel))))
                        .hoverEvent(HoverEvent.showText(text("Переключиться на ${channelName(channel)}: ${channelHint(channel)}")))
                } else entry
                line.append(shown).append(text("  "))
            }
        }

        /** Pause periods to choose from, or the end of the current pause with a button to lift it. */
        private fun holdLine(product: String, held: Boolean, heldUntil: Instant?, now: Instant, zone: ZoneId): Component {
            val editable = routes.interactive && routes.editable
            var line = text("  Пауза: ", UpdatePalette.MUTED)
            if (held) {
                line = line.append(text("${holdText(heldUntil, now, zone)} ", UpdatePalette.WARN))
                    .append(button("[Снять]", routes.set(product, "hold", "off"), "Снова предлагать и загружать обновления", UpdatePalette.OK))
                if (editable) line = line.append(text("  продлить: ", UpdatePalette.DIM))
            }
            if (!editable) return if (held) line else line.append(text("нет", UpdatePalette.DIM))
            HOLD_PERIODS.forEach { period ->
                line = line.append(text(periodLabel(period), UpdatePalette.WARN)
                    .clickEvent(ClickEvent.runCommand(routes.set(product, "hold", period)))
                    .hoverEvent(HoverEvent.showText(text("Не предлагать и не загружать обновления ${periodLabel(period)}"))))
                    .append(text("  "))
            }
            return line
        }

        private fun holdText(until: Instant?, now: Instant, zone: ZoneId): String =
            if (until == null) "задана в updates.yml"
            else "до ${fullTime.format(until.atZone(zone))} (${remaining(Duration.between(now, until))})"

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

        // --- lines -------------------------------------------------------------------------------------------

        private fun catalogLine(snapshot: UpdateSnapshot): Component {
            val staged = snapshot.state == UpdateState.UPDATE_STAGED || snapshot.state == UpdateState.DOWNLOADING
            var line = text("  ● ", dotColor(snapshot.state))
                .append(text(snapshot.product, UpdatePalette.TEXT)).append(text(" ${snapshot.currentVersion}", UpdatePalette.MUTED))
            val latest = snapshot.latestVersion?.takeIf { it != snapshot.currentVersion }
            val update = routes.update
            line = when {
                snapshot.state == UpdateState.UPDATE_AVAILABLE && update != null && routes.interactive ->
                    line.append(button(" [↑${latest?.let { " $it" }.orEmpty()}]", update(snapshot.product),
                        "Проверить и подготовить обновление. Применение — после перезапуска.", UpdatePalette.OK))
                snapshot.state == UpdateState.UPDATE_AVAILABLE ->
                    line.append(text(" → ${latest ?: "новая версия"}", UpdatePalette.OK))
                staged -> line.append(text(" → ${latest ?: "?"} ${stateLabel(snapshot.state)}", stateColor(snapshot.state)))
                snapshot.state != UpdateState.UP_TO_DATE -> line.append(text(" ${stateLabel(snapshot.state)}", stateColor(snapshot.state)))
                else -> line
            }
            if (snapshot.channel != UpdateChannel.STABLE) {
                line = line.append(text(" ${channelName(snapshot.channel)}", channelColor(snapshot.channel)))
            }
            snapshot.latestChangelogUrl?.takeIf(::safeLink)?.takeIf { snapshot.state in pending }?.let { link ->
                line = line.append(changelogButton(link))
            }
            line = line.append(button(" [i]", routes.info(snapshot.product), "Сведения о плагине", UpdatePalette.INFO_DIM))
            return line.hoverEvent(HoverEvent.showText(text(
                "Канал: ${channelName(snapshot.channel)}\nАвтозагрузка: ${if (snapshot.automaticDownload) "включена" else "выключена"}" +
                    snapshot.message?.takeIf { it.isNotBlank() }?.let { "\n${it.take(MAX_MESSAGE)}" }.orEmpty())))
        }

        private fun historyLines(entry: UpdateHistoryEntry, offerFilter: Boolean, zone: ZoneId): List<Component> {
            val (outcome, color) = outcome(entry)
            val time = text("  ${clock.format(entry.occurredAt.atZone(zone))} ", UpdatePalette.DIM)
            val actor = text(" · ${entry.actor.name}", UpdatePalette.DIM)
            fun hover(product: String?): HoverEvent<Component> = HoverEvent.showText(text(buildString {
                product?.let { appendLine(it) }
                appendLine("${operationLabel(entry.operation)} · ${entry.actor.kind.name.lowercase(Locale.ROOT)} ${entry.actor.name}")
                appendLine("${fullTime.format(entry.occurredAt.atZone(zone))} (${entry.occurredAt.atOffset(ZoneOffset.UTC)})")
                appendLine("План: ${entry.planId}")
                entry.message?.takeIf { it.isNotBlank() }?.let { appendLine(it.take(MAX_MESSAGE)) }
                if (offerFilter && product != null) append("Показать только $product: ${routes.history(product)}")
            }.trimEnd()))
            if (entry.changes.isEmpty()) {
                val body = entry.message?.takeIf { it.isNotBlank() }?.take(MAX_LINE) ?: operationLabel(entry.operation)
                return listOf(time.append(text("$body ", UpdatePalette.TEXT)).append(text(outcome, color)).append(actor).hoverEvent(hover(null)))
            }
            return entry.changes.map { change ->
                val transition = when (entry.operation) {
                    "ROLLBACK", "HEALTH_ROLLBACK" -> "откат к ${change.from ?: "не установлено"}"
                    ComponentLedger.OPERATION_ADDED -> "${change.to}"
                    ComponentLedger.OPERATION_REMOVED, ComponentLedger.OPERATION_SETTINGS_CHANGED -> "${change.from ?: change.to}"
                    else -> "${change.from ?: "-"} → ${change.to}"
                }
                var line = time.append(text("${change.product} ", UpdatePalette.TEXT))
                    .append(text("$transition ", UpdatePalette.MUTED))
                    .append(text(outcome, color))
                    .append(actor)
                    .hoverEvent(hover(change.product.toString()))
                if (offerFilter && routes.interactive) line = line.clickEvent(ClickEvent.runCommand(routes.history(change.product.toString())))
                line
            }
        }

        private fun outcome(entry: UpdateHistoryEntry): Pair<String, TextColor> = when {
            entry.operation == ComponentLedger.OPERATION_ADDED -> "установлен" to UpdatePalette.OK
            entry.operation == ComponentLedger.OPERATION_REMOVED -> "удалён" to UpdatePalette.DANGER
            entry.operation == ComponentLedger.OPERATION_VERSION_CHANGED -> "версия изменена вручную" to UpdatePalette.ORANGE
            entry.operation == ComponentLedger.OPERATION_SETTINGS_CHANGED -> (entry.message ?: "настройки изменены") to UpdatePalette.WARN
            entry.state == UpdateState.FAILED -> "ошибка" to UpdatePalette.DANGER
            entry.operation == "HEALTH" && entry.state == UpdateState.UP_TO_DATE -> "применено после перезапуска" to UpdatePalette.OK
            entry.operation == "HEALTH_ROLLBACK" && entry.state == UpdateState.ROLLED_BACK -> "откат подтверждён после перезапуска" to UpdatePalette.OK
            entry.operation == "ROLLBACK" || entry.state == UpdateState.ROLLED_BACK -> "откат подготовлен к перезапуску" to UpdatePalette.ORANGE
            entry.state == UpdateState.UPDATE_STAGED -> "подготовлено к перезапуску" to UpdatePalette.ORANGE
            entry.state == UpdateState.UPDATE_AVAILABLE -> "доступна новая версия" to UpdatePalette.INFO
            else -> stateLabel(entry.state) to UpdatePalette.MUTED
        }

        // --- building blocks ---------------------------------------------------------------------------------

        /** A row of mutually exclusive choices: the current one is marked in its color, the others are buttons. */
        private fun <T> choices(
            label: String,
            options: List<Pair<T, String>>,
            current: T,
            color: (T) -> TextColor,
            command: (T) -> Pair<String, String>,
        ): Component {
            var line = text("  $label: ", UpdatePalette.MUTED)
            options.forEach { (value, name) ->
                line = if (value == current) {
                    line.append(text("● $name", color(value)))
                } else if (routes.interactive && routes.editable) {
                    val (run, hint) = command(value)
                    line.append(text("○ $name", UpdatePalette.MUTED).clickEvent(ClickEvent.runCommand(run)).hoverEvent(HoverEvent.showText(text(hint))))
                } else {
                    line.append(text("○ $name", UpdatePalette.DIM))
                }
                line = line.append(text("  "))
            }
            return line
        }

        private fun historyNavigation(page: Int, totalPages: Int, base: String): Component {
            var line = text("  страница $page из $totalPages", UpdatePalette.DIM)
            if (page > 1) line = line.append(button(" [Новее]", "$base ${page - 1}", "Более свежие события", UpdatePalette.ACCENT))
            if (page < totalPages) line = line.append(button(" [Старее]", "$base ${page + 1}", "Более старые события", UpdatePalette.ACCENT))
            if (!routes.interactive && page < totalPages) line = line.append(text(" (следующая: $base ${page + 1})", UpdatePalette.DIM))
            return line.append(button(" [Плагины]", routes.list, "Все зарегистрированные компоненты", UpdatePalette.ACCENT))
        }

        private fun pager(page: Int, totalPages: Int, base: String): Component? {
            if (totalPages <= 1) return null
            var line = text("  страница $page из $totalPages", UpdatePalette.DIM)
            if (page > 1) line = line.append(button(" [Назад]", "$base ${page - 1}", "Предыдущая страница", UpdatePalette.ACCENT))
            if (page < totalPages) line = line.append(button(" [Далее]", "$base ${page + 1}", "Следующая страница", UpdatePalette.ACCENT))
            if (!routes.interactive) {
                line = line.append(text(if (page < totalPages) " (следующая: $base ${page + 1})" else "", UpdatePalette.DIM))
            }
            return line
        }

        /** A gradient name and muted details: the first line of every page. */
        private fun title(name: String, details: String, color: TextColor = UpdatePalette.MUTED): Component =
            UpdatePalette.gradient(if (routes.ascii) "> $name" else "✦ $name")
                .append(text(if (details.isBlank()) "" else "  $details", color))

        /** A thin gradient line between a page's content and its actions. */
        private fun rule(): Component = UpdatePalette.gradient((if (routes.ascii) "-" else "▬").repeat(RULE_WIDTH))

        private fun field(label: String, value: String, color: TextColor = UpdatePalette.TEXT): Component =
            text("  $label: ", UpdatePalette.MUTED).append(text(value, color))

        private fun actions(vararg buttons: Component): Component {
            val visible = buttons.filter { it != Component.empty() }
            return if (visible.isEmpty()) Component.empty()
            else visible.fold(text("  ")) { line, button -> line.append(button).append(text(" ")) }
        }

        /** A clickable label, or nothing when the reader cannot click; [commandHints] lists the commands instead. */
        private fun button(label: String, command: String, hint: String, color: TextColor): Component =
            if (!routes.interactive) Component.empty()
            else text(label, color).clickEvent(ClickEvent.runCommand(command)).hoverEvent(HoverEvent.showText(text(hint)))

        private fun changelogButton(link: String): Component =
            if (!routes.interactive) text(" $link", UpdatePalette.INFO)
            else text(" [Изменения]", UpdatePalette.INFO).clickEvent(ClickEvent.openUrl(link))
                .hoverEvent(HoverEvent.showText(text("Открыть список изменений")))

        /** The footer of a reader that cannot click: what to type instead of pressing the buttons. */
        private fun commandHints(vararg hints: Pair<String?, String>): Component {
            val parts = hints.filter { it.first != null }.joinToString("  ") { "${it.first} (${it.second})" }
            return text("  Команды: $parts", UpdatePalette.DIM)
        }

        private fun muted(value: String): Component = text("  $value", UpdatePalette.MUTED)

        private fun blank(): Component = Component.empty()

        private fun text(value: String, color: TextColor? = null, vararg decorations: TextDecoration): Component {
            val shown = if (routes.ascii) asciiOnly(value) else value
            var component = Component.text(shown)
            if (color != null) component = component.color(color)
            decorations.forEach { component = component.decoration(it, true) }
            return component
        }

        private fun asciiOnly(value: String): String = value
            .replace("→", "->").replace("↑", "^").replace("·", "|").replace("—", "-").replace("–", "-")
            .replace("●", "*").replace("○", "o")

        private fun <T> List<T>.page(page: Int): List<T> {
            val offset = (page.toLong() - 1) * PAGE_SIZE
            return if (offset >= size) emptyList() else drop(offset.toInt()).take(PAGE_SIZE)
        }

        private fun pageCount(total: Int): Int = maxOf(1, (total + PAGE_SIZE - 1) / PAGE_SIZE)

        private fun dayLabel(day: LocalDate, today: LocalDate): String = when {
            day == today -> "Сегодня"
            day == today.minusDays(1) -> "Вчера"
            day.year == today.year -> dayFormat.format(day)
            else -> dayYearFormat.format(day)
        }

        private fun age(at: Instant, now: Instant, zone: ZoneId): String {
            val elapsed = Duration.between(at, now)
            return when {
                elapsed.isNegative || elapsed.toMinutes() < 1 -> "только что"
                elapsed.toMinutes() < 60 -> "${elapsed.toMinutes()} мин назад"
                elapsed.toHours() < 24 -> "${elapsed.toHours()} ч назад"
                elapsed.toDays() < 60 -> "${elapsed.toDays()} д назад"
                else -> dayYearFormat.format(at.atZone(zone))
            }
        }

        private fun plural(count: Int, one: String, few: String, many: String): String {
            val word = when {
                count % 100 in 11..14 -> many
                count % 10 == 1 -> one
                count % 10 in 2..4 -> few
                else -> many
            }
            return "$count $word"
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
            UpdateState.UPDATE_AVAILABLE -> UpdatePalette.OK
            UpdateState.UPDATE_STAGED, UpdateState.DOWNLOADING, UpdateState.ROLLED_BACK -> UpdatePalette.ORANGE
            UpdateState.FAILED, UpdateState.BLOCKED, UpdateState.INCOMPATIBLE -> UpdatePalette.DANGER
            UpdateState.FROZEN -> UpdatePalette.WARN
            else -> UpdatePalette.MUTED
        }

        private fun dotColor(state: UpdateState): TextColor = when (state) {
            UpdateState.UP_TO_DATE -> UpdatePalette.OK
            UpdateState.UPDATE_AVAILABLE -> UpdatePalette.INFO
            UpdateState.CHECKING -> UpdatePalette.DIM
            else -> stateColor(state)
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
    private const val RULE_WIDTH = 24
}
