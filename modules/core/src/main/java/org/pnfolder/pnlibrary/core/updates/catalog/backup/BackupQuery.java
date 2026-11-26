package org.pnfolder.pnlibrary.core.updates.catalog.backup;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.updates.backup.UpdateBackup;
import org.pnfolder.pnlibrary.api.updates.backup.UpdateBackupComponent;

/**
 * What the administrator asked of the list of rollback sets: only sets that touch one plugin, only recent ones, and in
 * which order. The same value builds the command that shows the list again, so page and sort buttons keep the filters.
 */
public final class BackupQuery {
    /** What {@code since} accepts, for the error message. */
    public static final String SINCE_HELP = "7d (за 7 дней) или дата 2026-10-31";

    private static final Pattern DAYS = Pattern.compile("(\\d{1,4})d", Pattern.CASE_INSENSITIVE);

    private final String plugin;
    private final Instant since;
    private final String sinceText;
    private final BackupOrder order;

    /**
     * @param plugin product the sets must contain, or null for all
     * @param since oldest creation time that still counts, or null for any; a set without a recorded date never matches
     * @param sinceText what was typed for {@code since}, kept to rebuild the command
     * @param order how the matching sets are ordered
     */
    public BackupQuery(@Nullable String plugin, @Nullable Instant since, @Nullable String sinceText, @NotNull BackupOrder order) {
        this.plugin = plugin;
        this.since = since;
        this.sinceText = sinceText;
        this.order = Objects.requireNonNull(order, "order");
    }

    public BackupQuery() {
        this(null, null, null, BackupOrder.NEWEST);
    }

    public @Nullable String getPlugin() {
        return plugin;
    }

    public @Nullable Instant getSince() {
        return since;
    }

    public @Nullable String getSinceText() {
        return sinceText;
    }

    public @NotNull BackupOrder getOrder() {
        return order;
    }

    /** The same query in another order. */
    public @NotNull BackupQuery withOrder(@NotNull BackupOrder newOrder) {
        return new BackupQuery(plugin, since, sinceText, newOrder);
    }

    /** Whether no filter and the usual order are set. */
    public boolean isDefault() {
        return plugin == null && since == null && order == BackupOrder.NEWEST;
    }

    /** The sets that match, in the requested order. */
    public @NotNull List<UpdateBackup> apply(@NotNull List<UpdateBackup> backups) {
        List<UpdateBackup> matching = new ArrayList<>();
        for (UpdateBackup backup : backups) {
            if (touches(backup) && (since == null || backup.getCreatedAt() != null && !backup.getCreatedAt().isBefore(since))) {
                matching.add(backup);
            }
        }
        Collections.sort(matching, comparator());
        return matching;
    }

    private boolean touches(UpdateBackup backup) {
        if (plugin == null) {
            return true;
        }
        for (UpdateBackupComponent component : backup.getComponents()) {
            if (component.getProduct().getValue().equalsIgnoreCase(plugin)) {
                return true;
            }
        }
        return false;
    }

    private Comparator<UpdateBackup> comparator() {
        final Comparator<UpdateBackup> newest = (a, b) -> created(b, Instant.MIN).compareTo(created(a, Instant.MIN));
        switch (order) {
            case OLDEST:
                return (a, b) -> created(a, Instant.MAX).compareTo(created(b, Instant.MAX));
            case PLUGIN:
                return new Comparator<UpdateBackup>() {
                    @Override
                    public int compare(UpdateBackup a, UpdateBackup b) {
                        int byName = firstName(a).compareTo(firstName(b));
                        return byName != 0 ? byName : newest.compare(a, b);
                    }
                };
            default:
                return newest;
        }
    }

    private static Instant created(UpdateBackup backup, Instant fallback) {
        return backup.getCreatedAt() == null ? fallback : backup.getCreatedAt();
    }

    private static String firstName(UpdateBackup backup) {
        String first = null;
        for (UpdateBackupComponent component : backup.getComponents()) {
            String name = component.getProduct().getValue().toLowerCase(Locale.ROOT);
            if (first == null || name.compareTo(first) < 0) {
                first = name;
            }
        }
        return first == null ? "" : first;
    }

    /** The command that shows this list, built on {@code base} such as {@code /pn updates backups}; a page number can be appended. */
    public @NotNull String command(@NotNull String base) {
        StringBuilder text = new StringBuilder(base);
        if (plugin != null) {
            text.append(" plugin ").append(plugin);
        }
        if (sinceText != null) {
            text.append(" since ").append(sinceText);
        }
        if (order != BackupOrder.NEWEST) {
            text.append(" sort ").append(order.getWord());
        }
        return text.toString();
    }

    /** The moment {@code text} means: {@code 7d} is that many days before {@code now}, a date is the start of that day in {@code zone}; null otherwise. */
    public static @Nullable Instant parseSince(@NotNull String text, @NotNull Instant now, @NotNull ZoneId zone) {
        Matcher days = DAYS.matcher(text);
        if (days.matches()) {
            return now.minus(Long.parseLong(days.group(1)), ChronoUnit.DAYS);
        }
        try {
            return LocalDate.parse(text).atStartOfDay(zone).toInstant();
        } catch (DateTimeParseException notADate) {
            return null;
        }
    }

    /** The order that {@code word} names, or null. */
    public static @Nullable BackupOrder orderOf(@NotNull String word) {
        for (BackupOrder candidate : BackupOrder.values()) {
            if (candidate.getWord().equalsIgnoreCase(word)) {
                return candidate;
            }
        }
        return null;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof BackupQuery)) {
            return false;
        }
        BackupQuery that = (BackupQuery) other;
        return Objects.equals(plugin, that.plugin) && Objects.equals(since, that.since)
            && Objects.equals(sinceText, that.sinceText) && order == that.order;
    }

    @Override
    public int hashCode() {
        return Objects.hash(plugin, since, sinceText, order);
    }

    @Override
    public String toString() {
        return "BackupQuery(" + command("") + ")";
    }
}
