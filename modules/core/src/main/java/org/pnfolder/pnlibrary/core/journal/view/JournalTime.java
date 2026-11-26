package org.pnfolder.pnlibrary.core.journal.view;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.chat.ChatKit;
import org.pnfolder.pnlibrary.api.chat.ChatPalette;
import org.pnfolder.pnlibrary.common.text.RussianText;
import org.pnfolder.pnlibrary.common.time.DisplayZone;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Dates and times of the journal, always in the configured display zone ({@link DisplayZone}). */
public final class JournalTime {
    static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");
    static final DateTimeFormatter DAY_WITH_YEAR = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final Locale RU = new Locale("ru");
    static final DateTimeFormatter WEEKDAY = DateTimeFormatter.ofPattern("EEEE", RU);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM HH:mm:ss");
    private static final DateTimeFormatter TIME_WITH_YEAR = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    private static final DateTimeFormatter LONG_DAY = DateTimeFormatter.ofPattern("d MMMM", RU);
    private static final DateTimeFormatter LONG_DAY_YEAR = DateTimeFormatter.ofPattern("d MMMM yyyy 'года'", RU);
    private static final DateTimeFormatter UTC_STAMP =
        DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneOffset.UTC);
    private static final long MONTH_MILLIS = 30L * 86_400_000;
    private static final String AGO_SUFFIX = " назад";

    private JournalTime() {
    }

    /** The date and time; the year is shown only when it is not the current one, so old events are never ambiguous. */
    static @NotNull String stamp(long millis) {
        Instant instant = Instant.ofEpochMilli(millis);
        ZoneId zone = DisplayZone.getZone();
        boolean sameYear = instant.atZone(zone).getYear() == LocalDate.now(zone).getYear();
        return (sameYear ? TIME : TIME_WITH_YEAR).withZone(zone).format(instant);
    }

    /** "16 октября 2023 года", or "16 октября" within the current year. */
    static @NotNull String longDate(@NotNull LocalDate date) {
        boolean thisYear = date.getYear() == LocalDate.now(DisplayZone.getZone()).getYear();
        return (thisYear ? LONG_DAY : LONG_DAY_YEAR).format(date);
    }

    private static LocalDate localDate(long millis) {
        return Instant.ofEpochMilli(millis).atZone(DisplayZone.getZone()).toLocalDate();
    }

    /** "16 октября 2023 года, 16:45:07": the date in words, then the clock in the display zone. */
    static @NotNull String fullStamp(long millis) {
        return longDate(localDate(millis)) + ", "
            + CLOCK.withZone(DisplayZone.getZone()).format(Instant.ofEpochMilli(millis));
    }

    /** Numbers and UTC in the hover, so the exact value is always one hover away. */
    static @NotNull HoverEvent<Component> momentTip(@NotNull ChatKit kit, long millis) {
        String since = ago(millis);
        if (since.endsWith(AGO_SUFFIX)) {
            since = since.substring(0, since.length() - AGO_SUFFIX.length());
        }
        return kit.tooltip(
            kit.text(TIME_WITH_YEAR.withZone(DisplayZone.getZone()).format(Instant.ofEpochMilli(millis)),
                ChatPalette.TEXT),
            kit.text(WEEKDAY.format(localDate(millis)), ChatPalette.LABEL),
            kit.blank(),
            kit.hoverField("UTC", kit.text(UTC_STAMP.format(Instant.ofEpochMilli(millis)), ChatPalette.TEXT)),
            kit.hoverField("Часовой пояс", kit.text(zoneLabel(), ChatPalette.TEXT)),
            kit.hoverField("Прошло", kit.text(since, agoColor(millis))));
    }

    static @NotNull Component moment(@NotNull ChatKit kit, long millis) {
        return kit.text(fullStamp(millis), ChatPalette.TEXT).hoverEvent(momentTip(kit, millis));
    }

    public static @NotNull TextColor agoColor(long millis) {
        return agoColor(millis, System.currentTimeMillis());
    }

    /** Dimmer once the event is more than a month old. */
    public static @NotNull TextColor agoColor(long millis, long now) {
        return now - millis < MONTH_MILLIS ? ChatPalette.SOFT : ChatPalette.FAINT;
    }

    public static @NotNull String ago(long millis) {
        return ago(millis, System.currentTimeMillis(), DisplayZone.getZone());
    }

    /**
     * Two largest units: "только что", "5 мин назад", "3 ч 12 мин назад", "5 дней назад", "3 месяца 2 дня назад".
     */
    public static @NotNull String ago(long millis, long now, @NotNull ZoneId zone) {
        long seconds = Math.max((now - millis) / 1000, 0);
        if (seconds < 60) {
            return "только что";
        }
        if (seconds < 86_400) {
            long hours = seconds / 3600;
            long minutes = seconds % 3600 / 60;
            String span = hours == 0 ? minutes + " мин" : minutes == 0 ? hours + " ч" : hours + " ч " + minutes + " мин";
            return span + AGO_SUFFIX;
        }
        LocalDate from = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate();
        LocalDate to = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        Period period = Period.between(from, to);
        List<String> parts = new ArrayList<String>();
        addPart(parts, period.getYears(), "год", "года", "лет");
        addPart(parts, period.getMonths(), "месяц", "месяца", "месяцев");
        addPart(parts, period.getDays(), "день", "дня", "дней");
        String text = parts.isEmpty() ? "1 день" : String.join(" ", parts.subList(0, Math.min(2, parts.size())));
        return text + AGO_SUFFIX;
    }

    private static void addPart(List<String> parts, int count, String one, String few, String many) {
        if (count > 0) {
            parts.add(count + " " + RussianText.plural(count, one, few, many));
        }
    }

    /** The gap between repeats as "5 мин", "1 ч 20 мин" or "1 д 1 ч". */
    public static @NotNull String span(long millis) {
        long minutes = millis / 60_000;
        if (minutes < 1) {
            return "меньше минуты";
        }
        if (minutes < 60) {
            return minutes + " мин";
        }
        if (minutes < 1440) {
            return minutes / 60 + " ч " + minutes % 60 + " мин";
        }
        return minutes / 1440 + " д " + minutes % 1440 / 60 + " ч";
    }

    /** The zone with its offset, so nobody has to guess which clock a time uses. */
    static @NotNull String zoneLabel() {
        ZoneId zone = DisplayZone.getZone();
        String offset = zone.getRules().getOffset(Instant.now()).getId();
        return zone.getId() + " (UTC" + ("Z".equals(offset) ? "+00:00" : offset) + ")";
    }
}
