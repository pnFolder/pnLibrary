package org.pnfolder.pnlibrary.core.updates.catalog;

import java.util.Objects;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.chat.ChatKit;
import org.pnfolder.pnlibrary.api.chat.ChatPalette;
import org.pnfolder.pnlibrary.api.chat.ChatReader;
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel;
import org.pnfolder.pnlibrary.api.updates.release.ReleaseSummary;
import org.pnfolder.pnlibrary.api.updates.state.UpdateSnapshot;
import org.pnfolder.pnlibrary.common.text.RussianText;
import org.pnfolder.pnlibrary.updates.policy.UpdateChannelLabels;

/**
 * {@link ChatKit} with what every catalog page shares: the routes it links to, versions, release dates, channel tags
 * and pagination.
 */
public class CatalogStyle extends ChatKit {
    private static final Locale RUSSIAN = new Locale("ru");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM", RUSSIAN);
    private static final DateTimeFormatter DAY_YEAR = DateTimeFormatter.ofPattern("d MMMM uuuu", RUSSIAN);
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter FULL_TIME = DateTimeFormatter.ofPattern("d MMM HH:mm", RUSSIAN);
    private static final long AGE_DAYS = 60L;

    private final UpdateCatalogRoutes routes;
    private final Instant now;
    private final ZoneId zone;

    /**
     * @param now reference for release ages and "today"
     * @param zone zone used for days and clock times
     */
    public CatalogStyle(@NotNull UpdateCatalogRoutes routes, @NotNull Instant now, @NotNull ZoneId zone) {
        super(new ChatReader(routes.getInteractive(), routes.getAscii()));
        this.routes = Objects.requireNonNull(routes, "routes");
        this.now = Objects.requireNonNull(now, "now");
        this.zone = Objects.requireNonNull(zone, "zone");
    }

    public @NotNull UpdateCatalogRoutes getRoutes() {
        return routes;
    }

    public @NotNull Instant getNow() {
        return now;
    }

    public @NotNull ZoneId getZone() {
        return zone;
    }

    /** Whether this reader may change settings by clicking. */
    public boolean isClickable() {
        return routes.getInteractive() && routes.getEditable();
    }

    /** Colored tag with the channel name. */
    public @NotNull Component channelTag(@NotNull UpdateChannel channel) {
        return text(UpdateChannelLabels.commandName(channel), UpdateChannelLabels.color(channel));
    }

    /** The current version, then an arrow and the highlighted new one when there is an update. */
    public @NotNull Component versions(@NotNull String current, @Nullable String update) {
        return versions(current, update, null);
    }

    /** Same as {@link #versions(String, String)}; with a changelog the new version opens the list of changes. */
    public @NotNull Component versions(@NotNull String current, @Nullable String update, @Nullable String changelog) {
        Component base = text(version(current), ChatPalette.SOFT);
        if (update == null) {
            return base;
        }
        Component next = text(version(update), ChatPalette.INFO);
        if (changelog != null) {
            next = next.clickEvent(ClickEvent.openUrl(changelog)).hoverEvent(tooltip(
                text("Список изменений " + version(update), ChatPalette.TEXT),
                text(changelog.length() > MAX_LINE ? changelog.substring(0, MAX_LINE) : changelog, ChatPalette.LABEL)));
        }
        return base.append(text(" " + glyph('→') + " ", ChatPalette.SOFT)).append(next);
    }

    /** Versions that start with a digit read as {@code v1.2.0}; anything else is shown as published. */
    public @NotNull String version(@NotNull String value) {
        return !value.isEmpty() && Character.isDigit(value.charAt(0)) ? "v" + value : value;
    }

    /** The version a pending update moves to, or null when nothing is pending. */
    public @Nullable String updateTarget(@NotNull UpdateSnapshot snapshot) {
        String latest = snapshot.getLatestVersion();
        return latest != null && !latest.equals(snapshot.getCurrentVersion()) && CatalogLabels.PENDING.contains(snapshot.getState())
            ? latest : null;
    }

    /** Publication time of {@code version} if the snapshot knows it. */
    public @Nullable Instant releasedAt(@NotNull UpdateSnapshot snapshot, @NotNull String version) {
        for (ReleaseSummary release : snapshot.getAvailableReleases()) {
            if (release.getVersion().equals(version)) {
                return release.getPublishedAt();
            }
        }
        return null;
    }

    /** A release date, with its age while it is recent: {@code 1 октября · 16 д назад}. */
    public @NotNull String released(@NotNull Instant at) {
        LocalDate day = at.atZone(zone).toLocalDate();
        String date = day.getYear() == now.atZone(zone).getYear() ? DAY.format(day) : DAY_YEAR.format(day);
        return Duration.between(at, now).toDays() < AGE_DAYS ? date + " · " + age(at) : date;
    }

    /** How long ago {@code at} was, or its date once that is more than two months ago. */
    public @NotNull String age(@NotNull Instant at) {
        Duration elapsed = Duration.between(at, now);
        return elapsed.toDays() < AGE_DAYS ? RussianText.ago(elapsed) : DAY_YEAR.format(at.atZone(zone));
    }

    /** Heading for a history day: today, yesterday or the date. */
    public @NotNull String dayLabel(@NotNull LocalDate day) {
        LocalDate today = now.atZone(zone).toLocalDate();
        if (day.equals(today)) {
            return "Сегодня";
        }
        if (day.equals(today.minusDays(1))) {
            return "Вчера";
        }
        return day.getYear() == today.getYear() ? DAY.format(day) : DAY_YEAR.format(day);
    }

    /** Time of day in the display zone. */
    public @NotNull String clock(@NotNull Instant at) {
        return CLOCK.format(at.atZone(zone));
    }

    /** Date and time in the display zone. */
    public @NotNull String fullTime(@NotNull Instant at) {
        return FULL_TIME.format(at.atZone(zone));
    }

    /** {@code [Изменения]} that opens the changelog, or the link itself for a reader that cannot click. */
    public @NotNull Component changelogButton(@NotNull String link) {
        if (!routes.getInteractive()) {
            return text(" " + link, ChatPalette.LABEL);
        }
        return text(" ").append(linkButton("Изменения", link, "Открыть список изменений", ChatPalette.INFO));
    }

    /** Button that opens the support link. */
    public @NotNull Component supportButton(@NotNull String link) {
        return linkButton("Поддержка", link, "Открыть поддержку pnFolder", ChatPalette.WARN);
    }

    /** Previous and next page buttons around the page number; nothing for a single page. */
    public @Nullable Component pager(int page, int totalPages, @NotNull String base, @NotNull String previous, @NotNull String next) {
        if (totalPages <= 1) {
            return null;
        }
        Component line = text("  ").append(text("страница ", ChatPalette.LABEL)).append(text(String.valueOf(page), ChatPalette.ACCENT))
            .append(text(" из ", ChatPalette.LABEL)).append(text(String.valueOf(totalPages), ChatPalette.ACCENT));
        if (page > 1) {
            line = line.append(text(" ")).append(button("[" + previous + "]", base + " " + (page - 1), "Страница " + (page - 1), ChatPalette.ACCENT));
        }
        if (page < totalPages) {
            line = line.append(text(" ")).append(button("[" + next + "]", base + " " + (page + 1), "Страница " + (page + 1), ChatPalette.ACCENT));
        }
        if (!routes.getInteractive() && page < totalPages) {
            line = line.append(text(" (следующая: " + base + " " + (page + 1) + ")", ChatPalette.LABEL));
        }
        return line;
    }

    /** A counter whose word agrees with the number: {@code 3 плагина}. */
    public @NotNull Component counter(int count, @NotNull String one, @NotNull String few, @NotNull String many) {
        return counter(count, one, few, many, ChatPalette.ACCENT);
    }

    public @NotNull Component counter(int count, @NotNull String one, @NotNull String few, @NotNull String many, @NotNull TextColor color) {
        return chip(count, RussianText.plural(count, one, few, many), color);
    }

    /** The entries of one page; pages are one-based. */
    public <T> @NotNull List<T> onPage(@NotNull List<T> all, int page) {
        long offset = ((long) page - 1) * UpdateCatalogView.PAGE_SIZE;
        if (offset >= all.size()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(all.subList((int) offset, (int) Math.min(offset + UpdateCatalogView.PAGE_SIZE, all.size())));
    }

    /** Number of pages for {@code total} items, at least one. */
    public int pageCount(int total) {
        return Math.max(1, (total + UpdateCatalogView.PAGE_SIZE - 1) / UpdateCatalogView.PAGE_SIZE);
    }
}
