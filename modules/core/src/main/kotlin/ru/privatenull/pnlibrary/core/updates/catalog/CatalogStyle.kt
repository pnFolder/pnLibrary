package ru.privatenull.pnlibrary.core.updates.catalog

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.TextColor
import ru.privatenull.pnlibrary.api.updates.UpdateChannel
import ru.privatenull.pnlibrary.api.updates.UpdateSnapshot
import ru.privatenull.pnlibrary.common.text.RussianText
import ru.privatenull.pnlibrary.core.ui.ChatKit
import ru.privatenull.pnlibrary.core.ui.ChatPalette
import ru.privatenull.pnlibrary.core.ui.ChatReader
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * [ChatKit] with what every catalog page shares: the routes it links to, versions, release dates, channel tags
 * and pagination.
 *
 * @property now reference for release ages and "today"
 * @property zone zone used for days and clock times
 */
internal open class CatalogStyle(
    val routes: UpdateCatalogRoutes,
    val now: Instant,
    val zone: ZoneId,
) : ChatKit(ChatReader(routes.interactive, routes.ascii)) {
    /** Whether this reader may change settings by clicking. */
    val clickable: Boolean = routes.interactive && routes.editable

    fun channelTag(channel: UpdateChannel): Component =
        text(CatalogLabels.channelName(channel), CatalogLabels.channelColor(channel))

    /**
     * The current version, then an arrow and the highlighted new one when there is an update. With [changelog]
     * the new version opens the list of changes.
     */
    fun versions(current: String, update: String?, changelog: String? = null): Component {
        val base = text(version(current), ChatPalette.SOFT)
        if (update == null) return base
        var next = text(version(update), ChatPalette.INFO)
        if (changelog != null) {
            next = next.clickEvent(ClickEvent.openUrl(changelog)).hoverEvent(
                tooltip(text("Список изменений ${version(update)}", ChatPalette.TEXT), text(changelog.take(MAX_LINE), ChatPalette.LABEL)),
            )
        }
        return base.append(text(" ${glyph('→')} ", ChatPalette.SOFT)).append(next)
    }

    /** Versions that start with a digit read as `v1.2.0`; anything else is shown as published. */
    fun version(value: String): String = if (value.firstOrNull()?.isDigit() == true) "v$value" else value

    /** The version a pending update moves to, or null when nothing is pending. */
    fun updateTarget(snapshot: UpdateSnapshot): String? =
        snapshot.latestVersion?.takeIf { it != snapshot.currentVersion && snapshot.state in CatalogLabels.PENDING }

    fun releasedAt(snapshot: UpdateSnapshot, version: String): Instant? =
        snapshot.availableReleases.firstOrNull { it.version == version }?.publishedAt

    /** A release date, with its age while it is recent: `1 октября · 16 д назад`. */
    fun released(at: Instant): String {
        val day = at.atZone(zone).toLocalDate()
        val date = if (day.year == now.atZone(zone).year) DAY.format(day) else DAY_YEAR.format(day)
        return if (Duration.between(at, now).toDays() < AGE_DAYS) "$date · ${age(at)}" else date
    }

    /** How long ago [at] was, or its date once that is more than two months ago. */
    fun age(at: Instant): String {
        val elapsed = Duration.between(at, now)
        return if (elapsed.toDays() < AGE_DAYS) RussianText.ago(elapsed) else DAY_YEAR.format(at.atZone(zone))
    }

    fun dayLabel(day: LocalDate): String {
        val today = now.atZone(zone).toLocalDate()
        return when {
            day == today -> "Сегодня"
            day == today.minusDays(1) -> "Вчера"
            day.year == today.year -> DAY.format(day)
            else -> DAY_YEAR.format(day)
        }
    }

    fun clock(at: Instant): String = CLOCK.format(at.atZone(zone))

    fun fullTime(at: Instant): String = FULL_TIME.format(at.atZone(zone))

    /** `[Изменения]` that opens the changelog, or the link itself for a reader that cannot click. */
    fun changelogButton(link: String): Component =
        if (!routes.interactive) text(" $link", ChatPalette.LABEL)
        else text(" ").append(linkButton("Изменения", link, "Открыть список изменений", ChatPalette.INFO))

    fun supportButton(link: String): Component = linkButton("Поддержка", link, "Открыть поддержку pnFolder", ChatPalette.WARN)

    /** Previous and next page buttons around the page number; nothing for a single page. */
    fun pager(page: Int, totalPages: Int, base: String, previous: String, next: String): Component? {
        if (totalPages <= 1) return null
        var line = text("  ").append(text("страница ", ChatPalette.LABEL)).append(text("$page", ChatPalette.ACCENT))
            .append(text(" из ", ChatPalette.LABEL)).append(text("$totalPages", ChatPalette.ACCENT))
        if (page > 1) line = line.append(text(" ")).append(button("[$previous]", "$base ${page - 1}", "Страница ${page - 1}", ChatPalette.ACCENT))
        if (page < totalPages) line = line.append(text(" ")).append(button("[$next]", "$base ${page + 1}", "Страница ${page + 1}", ChatPalette.ACCENT))
        if (!routes.interactive && page < totalPages) line = line.append(text(" (следующая: $base ${page + 1})", ChatPalette.LABEL))
        return line
    }

    /** A counter whose word agrees with the number: `3 плагина`. */
    fun counter(count: Int, one: String, few: String, many: String, color: TextColor = ChatPalette.ACCENT): Component =
        chip(count, RussianText.plural(count.toLong(), one, few, many), color)

    /** The entries of one page; pages are one-based. */
    fun <T> List<T>.onPage(page: Int): List<T> {
        val offset = (page.toLong() - 1) * UpdateCatalogView.PAGE_SIZE
        return if (offset >= size) emptyList() else drop(offset.toInt()).take(UpdateCatalogView.PAGE_SIZE)
    }

    fun pageCount(total: Int): Int = maxOf(1, (total + UpdateCatalogView.PAGE_SIZE - 1) / UpdateCatalogView.PAGE_SIZE)

    companion object {
        private val RUSSIAN = Locale("ru")
        private val DAY = DateTimeFormatter.ofPattern("d MMMM", RUSSIAN)
        private val DAY_YEAR = DateTimeFormatter.ofPattern("d MMMM uuuu", RUSSIAN)
        private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")
        private val FULL_TIME = DateTimeFormatter.ofPattern("d MMM HH:mm", RUSSIAN)
        private const val AGE_DAYS = 60L
    }
}
