package ru.privatenull.pnlibrary.core.updates

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import ru.privatenull.pnlibrary.api.updates.UpdateBackup
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
 */
data class UpdateCatalogRoutes(
    val root: String,
    val update: ((String) -> String)? = null,
    val check: String = "$root check",
) {
    val list: String get() = "$root list"
    val available: String get() = "$root available"
    val history: String get() = "$root history"
    val backups: String get() = "$root backups"
    fun info(product: String): String = "$root info $product"
    fun history(product: String): String = "$root history plugin $product"
    fun rollback(id: Any, configurations: Boolean = false): String = "$root rollback $id" + if (configurations) " configs" else ""
}

/**
 * Shared chat and console presentation of the managed update catalog.
 *
 * Pages follow one layout: a bold purple title with counters, compact lines with inline buttons, and a
 * footer with pagination and navigation. Nothing here calls the server or changes updates, and stored text
 * is only ever rendered as plain text, never interpreted as markup.
 */
object UpdateCatalogView {
    /** Entries per page, shared with the history reader. */
    const val PAGE_SIZE = 10

    private val pending = setOf(UpdateState.UPDATE_AVAILABLE, UpdateState.UPDATE_STAGED, UpdateState.DOWNLOADING)
    private val russian = Locale("ru")
    private val dayFormat = DateTimeFormatter.ofPattern("d MMMM", russian)
    private val dayYearFormat = DateTimeFormatter.ofPattern("d MMMM uuuu", russian)
    private val clock = DateTimeFormatter.ofPattern("HH:mm")
    private val fullTime = DateTimeFormatter.ofPattern("d MMM HH:mm", russian)

    /** Renders one page of registered components, never the native plugin inventory. */
    fun catalog(snapshots: List<UpdateSnapshot>, page: Int, updatesOnly: Boolean, routes: UpdateCatalogRoutes): List<Component> {
        require(page > 0) { "page must be positive" }
        val selected = snapshots.filter { !updatesOnly || it.state in pending }.sortedBy { it.product.lowercase() }
        val totalPages = pageCount(selected.size)
        val entries = selected.page(page)
        val available = selected.count { it.state == UpdateState.UPDATE_AVAILABLE }
        val result = mutableListOf(
            title(if (updatesOnly) "Обновления" else "Каталог",
                "${plural(selected.size, "плагин", "плагина", "плагинов")} · $available к обновлению"),
        )
        entries.forEach { snapshot -> result += catalogLine(snapshot, routes) }
        if (entries.isEmpty()) result += muted(if (selected.isEmpty()) "Нет компонентов." else "Нет компонентов на этой странице.")
        result += blank()
        result += actions(
            button("[Проверить]", routes.check, "Проверить новые версии", NamedTextColor.GREEN),
            button(if (updatesOnly) "[Все]" else "[Обновления]", if (updatesOnly) routes.list else routes.available,
                if (updatesOnly) "Все зарегистрированные компоненты" else "Только доступные и подготовленные обновления",
                NamedTextColor.LIGHT_PURPLE),
            button("[История]", routes.history, "Кто, когда и что обновлял", NamedTextColor.LIGHT_PURPLE),
            button("[Резервы]", routes.backups, "Сохранённые версии для отката", NamedTextColor.LIGHT_PURPLE),
        )
        pager(page, totalPages, if (updatesOnly) routes.available else routes.list)?.let { result += it }
        return result
    }

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
    ): List<Component> {
        require(page > 0) { "page must be positive" }
        val result = mutableListOf(title("История", product ?: ""))
        var day: LocalDate? = null
        entries.forEach { entry ->
            val entryDay = entry.occurredAt.atZone(zone).toLocalDate()
            if (entryDay != day) {
                day = entryDay
                result += blank()
                result += Component.text(" ${dayLabel(entryDay, now.atZone(zone).toLocalDate())}", NamedTextColor.LIGHT_PURPLE)
            }
            result += historyLines(entry, routes, product == null, zone)
        }
        if (entries.isEmpty()) result += muted("Нет событий на этой странице.")
        result += blank()
        val base = if (product == null) routes.history else routes.history(product)
        val pages = totalPages ?: if (entries.size >= PAGE_SIZE) page + 1 else page
        val navigation = historyNavigation(page, pages, base, routes)
        result += navigation
        return result
    }

    /** Renders retained rollback sets with their age; paths and file contents are never shown. */
    fun backups(
        backups: List<UpdateBackup>,
        page: Int,
        routes: UpdateCatalogRoutes,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Component> {
        require(page > 0) { "page must be positive" }
        val selected = backups.page(page)
        val result = mutableListOf(title("Резервы", plural(backups.size, "набор", "набора", "наборов")))
        selected.forEach { backup ->
            val age = backup.createdAt?.let { age(it, now, zone) } ?: "дата не записана"
            var line: Component = Component.text("  ")
                .append(Component.text(backup.components.joinToString { component ->
                    "${component.product} ${component.backupVersion ?: "?"}"
                }, NamedTextColor.WHITE))
                .append(Component.text(" $age", NamedTextColor.GRAY))
                .hoverEvent(HoverEvent.showText(Component.text(
                    "Набор: ${backup.id}\nОбновлено до: ${backup.components.joinToString { "${it.product} ${it.updatedVersion ?: "?"}" }}" +
                        "\nКонфигураций: ${backup.configurationFiles}")))
            line = line.append(button(" [Откат]", routes.rollback(backup.id), "Открыть подтверждение отката без изменения конфигураций", NamedTextColor.YELLOW))
            if (backup.configurationFiles > 0) {
                line = line.append(button(" [+Конфиг]", routes.rollback(backup.id, true),
                    "Открыть подтверждение отката JAR и выбранных конфигураций", NamedTextColor.GOLD))
            }
            result += line
        }
        if (selected.isEmpty()) result += muted(if (backups.isEmpty()) "Нет сохранённых наборов." else "Нет наборов на этой странице.")
        result += blank()
        result += actions(
            button("[Плагины]", routes.list, "Все зарегистрированные компоненты", NamedTextColor.LIGHT_PURPLE),
            button("[История]", routes.history, "Кто, когда и что обновлял", NamedTextColor.LIGHT_PURPLE),
        )
        pager(page, pageCount(backups.size), routes.backups)?.let { result += it }
        return result
    }

    /** Renders the details card of one component. */
    fun info(snapshot: UpdateSnapshot, routes: UpdateCatalogRoutes): List<Component> {
        val result = mutableListOf<Component>(
            Component.text(snapshot.product, NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                .append(Component.text(" ${stateLabel(snapshot.state)}", stateColor(snapshot.state))),
        )
        snapshot.message?.takeIf { it.isNotBlank() }?.let { result += Component.text(" ${it.take(MAX_MESSAGE)}", NamedTextColor.GRAY) }
        result += blank()
        result += field("Версия", snapshot.currentVersion)
        snapshot.latestVersion?.takeIf { it != snapshot.currentVersion }?.let { latest ->
            var line = field("Доступна", latest, NamedTextColor.GREEN)
            snapshot.latestChangelogUrl?.takeIf(::safeLink)?.let { link ->
                line = line.append(Component.text(" [Изменения]", NamedTextColor.AQUA).clickEvent(ClickEvent.openUrl(link))
                    .hoverEvent(HoverEvent.showText(Component.text("Открыть список изменений"))))
            }
            result += line
        }
        result += field("Канал", snapshot.channel.name.lowercase(Locale.ROOT))
        result += field("Автозагрузка", if (snapshot.automaticDownload) "включена" else "выключена")
        snapshot.supportedApi?.let { result += field("API", "${it.minimum}–${it.maximum}") }
        result += field("Java", "${snapshot.currentJava} (нужна ${snapshot.requiredJava}+)")
        result += blank()
        val buttons = mutableListOf<Component>()
        routes.update?.takeIf { snapshot.state == UpdateState.UPDATE_AVAILABLE }?.let { update ->
            buttons += button("[Обновить]", update(snapshot.product), "Проверить и подготовить обновление. Применение — после перезапуска.", NamedTextColor.GREEN)
        }
        buttons += button("[История]", routes.history(snapshot.product), "События только этого плагина", NamedTextColor.LIGHT_PURPLE)
        buttons += button("[Назад]", routes.list, "К списку плагинов", NamedTextColor.GRAY)
        result += actions(*buttons.toTypedArray())
        return result
    }

    private fun catalogLine(snapshot: UpdateSnapshot, routes: UpdateCatalogRoutes): Component {
        val staged = snapshot.state == UpdateState.UPDATE_STAGED || snapshot.state == UpdateState.DOWNLOADING
        var line: Component = Component.text("  ${snapshot.product}", NamedTextColor.WHITE)
            .append(Component.text(" ${snapshot.currentVersion}", NamedTextColor.GRAY))
        val latest = snapshot.latestVersion?.takeIf { it != snapshot.currentVersion }
        val update = routes.update
        line = when {
            snapshot.state == UpdateState.UPDATE_AVAILABLE && update != null ->
                line.append(button(" [↑${latest?.let { " $it" }.orEmpty()}]", update(snapshot.product),
                    "Проверить и подготовить обновление. Применение — после перезапуска.", NamedTextColor.GREEN))
            snapshot.state == UpdateState.UPDATE_AVAILABLE ->
                line.append(Component.text(" → ${latest ?: "новая версия"}", NamedTextColor.GREEN))
            staged -> line.append(Component.text(" → ${latest ?: "?"} ${stateLabel(snapshot.state)}", stateColor(snapshot.state)))
            snapshot.state != UpdateState.UP_TO_DATE -> line.append(Component.text(" ${stateLabel(snapshot.state)}", stateColor(snapshot.state)))
            else -> line
        }
        snapshot.latestChangelogUrl?.takeIf(::safeLink)?.takeIf { snapshot.state in pending }?.let { link ->
            line = line.append(Component.text(" [Изменения]", NamedTextColor.AQUA).clickEvent(ClickEvent.openUrl(link))
                .hoverEvent(HoverEvent.showText(Component.text("Открыть список изменений"))))
        }
        line = line.append(button(" [i]", routes.info(snapshot.product), "Сведения о плагине", NamedTextColor.DARK_AQUA))
        return line.hoverEvent(HoverEvent.showText(Component.text(
            "Канал: ${snapshot.channel.name.lowercase(Locale.ROOT)}\nАвтозагрузка: ${if (snapshot.automaticDownload) "включена" else "выключена"}" +
                snapshot.message?.takeIf { it.isNotBlank() }?.let { "\n${it.take(MAX_MESSAGE)}" }.orEmpty())))
    }

    private fun historyLines(entry: UpdateHistoryEntry, routes: UpdateCatalogRoutes, offerFilter: Boolean, zone: ZoneId): List<Component> {
        val (outcome, color) = outcome(entry)
        val time = Component.text("  ${clock.format(entry.occurredAt.atZone(zone))} ", NamedTextColor.DARK_GRAY)
        val actor = Component.text(" · ${entry.actor.name}", NamedTextColor.DARK_GRAY)
        fun hover(product: String?): HoverEvent<Component> = HoverEvent.showText(Component.text(buildString {
            product?.let { appendLine(it) }
            appendLine("${operationLabel(entry.operation)} · ${entry.actor.kind.name.lowercase(Locale.ROOT)} ${entry.actor.name}")
            appendLine("${fullTime.format(entry.occurredAt.atZone(zone))} (${entry.occurredAt.atOffset(ZoneOffset.UTC)})")
            appendLine("План: ${entry.planId}")
            entry.message?.takeIf { it.isNotBlank() }?.let { appendLine(it.take(MAX_MESSAGE)) }
            if (offerFilter && product != null) append("Показать только $product: ${routes.history(product)}")
        }.trimEnd()))
        if (entry.changes.isEmpty()) {
            val text = entry.message?.takeIf { it.isNotBlank() }?.take(MAX_LINE) ?: operationLabel(entry.operation)
            return listOf(time.append(Component.text("$text ", NamedTextColor.WHITE)).append(Component.text(outcome, color)).append(actor)
                .hoverEvent(hover(null)))
        }
        return entry.changes.map { change ->
            val transition = if (entry.operation == "ROLLBACK" || entry.operation == "HEALTH_ROLLBACK") {
                "откат к ${change.from ?: "не установлено"}"
            } else "${change.from ?: "—"} → ${change.to}"
            var line = time.append(Component.text("${change.product} ", NamedTextColor.WHITE))
                .append(Component.text("$transition ", NamedTextColor.GRAY))
                .append(Component.text(outcome, color))
                .append(actor)
                .hoverEvent(hover(change.product.toString()))
            if (offerFilter) line = line.clickEvent(ClickEvent.runCommand(routes.history(change.product.toString())))
            line
        }
    }

    private fun outcome(entry: UpdateHistoryEntry): Pair<String, NamedTextColor> = when {
        entry.state == UpdateState.FAILED -> "ошибка" to NamedTextColor.RED
        entry.operation == "HEALTH" && entry.state == UpdateState.UP_TO_DATE -> "применено после перезапуска" to NamedTextColor.GREEN
        entry.operation == "HEALTH_ROLLBACK" && entry.state == UpdateState.ROLLED_BACK -> "откат подтверждён после перезапуска" to NamedTextColor.GREEN
        entry.operation == "ROLLBACK" || entry.state == UpdateState.ROLLED_BACK -> "откат подготовлен к перезапуску" to NamedTextColor.GOLD
        entry.state == UpdateState.UPDATE_STAGED -> "подготовлено к перезапуску" to NamedTextColor.GOLD
        entry.state == UpdateState.UPDATE_AVAILABLE -> "доступна новая версия" to NamedTextColor.AQUA
        else -> stateLabel(entry.state) to NamedTextColor.GRAY
    }

    private fun historyNavigation(page: Int, totalPages: Int, base: String, routes: UpdateCatalogRoutes): Component {
        var line: Component = Component.text("  страница $page из $totalPages", NamedTextColor.DARK_GRAY)
        if (page > 1) line = line.append(button(" [Новее]", "$base ${page - 1}", "Более свежие события", NamedTextColor.LIGHT_PURPLE))
        if (page < totalPages) line = line.append(button(" [Старее]", "$base ${page + 1}", "Более старые события", NamedTextColor.LIGHT_PURPLE))
        return line.append(button(" [Плагины]", routes.list, "Все зарегистрированные компоненты", NamedTextColor.LIGHT_PURPLE))
    }

    private fun pager(page: Int, totalPages: Int, base: String): Component? {
        if (totalPages <= 1) return null
        var line: Component = Component.text("  страница $page из $totalPages", NamedTextColor.DARK_GRAY)
        if (page > 1) line = line.append(button(" [Назад]", "$base ${page - 1}", "Предыдущая страница", NamedTextColor.LIGHT_PURPLE))
        if (page < totalPages) line = line.append(button(" [Далее]", "$base ${page + 1}", "Следующая страница", NamedTextColor.LIGHT_PURPLE))
        return line
    }

    private fun title(name: String, details: String): Component =
        Component.text(name, NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
            .append(Component.text(if (details.isBlank()) "" else " $details", NamedTextColor.GRAY))

    private fun field(label: String, value: String, color: NamedTextColor = NamedTextColor.WHITE): Component =
        Component.text("  $label: ", NamedTextColor.GRAY).append(Component.text(value, color))

    private fun actions(vararg buttons: Component): Component =
        buttons.fold(Component.text("  ")) { line, button -> line.append(button).append(Component.text(" ")) }

    private fun button(label: String, command: String, hint: String, color: NamedTextColor): Component =
        Component.text(label, color).clickEvent(ClickEvent.runCommand(command))
            .hoverEvent(HoverEvent.showText(Component.text(hint)))

    private fun muted(text: String): Component = Component.text("  $text", NamedTextColor.GRAY)

    private fun blank(): Component = Component.empty()

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

    private fun stateColor(state: UpdateState): NamedTextColor = when (state) {
        UpdateState.UPDATE_AVAILABLE -> NamedTextColor.GREEN
        UpdateState.UPDATE_STAGED, UpdateState.DOWNLOADING, UpdateState.ROLLED_BACK -> NamedTextColor.GOLD
        UpdateState.FAILED -> NamedTextColor.RED
        UpdateState.BLOCKED, UpdateState.INCOMPATIBLE -> NamedTextColor.RED
        UpdateState.FROZEN -> NamedTextColor.YELLOW
        else -> NamedTextColor.GRAY
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
        else -> "состояние"
    }

    private const val MAX_MESSAGE = 512
    private const val MAX_LINE = 120
}
