package org.pnfolder.pnlibrary.core.settings;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import org.pnfolder.pnlibrary.common.lang.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A list of values a setting is chosen from, for settings that have too many values for a switch.
 *
 * <p>The list is computed where it is needed (java.time is on every server, proxy and client), so the link between a
 * proxy and a game server sends only the {@link #getId() id}, never the values.
 */
public final class ChoiceSource {
    /** The id of {@link #ZONES}. */
    public static final String ZONE_ID = "zones";

    private static final ChoiceOrder ALPHABET = new ChoiceOrder("по алфавиту", (values, at) -> {
        List<String> sorted = new ArrayList<String>(values);
        Collections.sort(sorted);
        return sorted;
    });
    private static final List<String> REGIONS = Arrays.asList(
        "Africa", "America", "Antarctica", "Asia", "Atlantic", "Australia", "Europe", "Indian", "Pacific");
    private static final List<String> EXTRA = Arrays.asList("UTC", "Etc/UTC");
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final Pattern BLANKS = Pattern.compile("\\s+");
    private static final int SECONDS_IN_HOUR = 3600;
    private static final int SECONDS_IN_MINUTE = 60;
    private static final List<String> POPULAR_ZONES = Arrays.asList(
        "UTC", "Europe/London", "Europe/Berlin", "Europe/Kyiv", "Europe/Minsk", "Europe/Moscow", "Europe/Istanbul",
        "Asia/Dubai", "Asia/Tashkent", "Asia/Almaty", "Asia/Kolkata", "Asia/Bangkok", "Asia/Shanghai", "Asia/Tokyo",
        "Australia/Sydney", "Pacific/Auckland", "America/Sao_Paulo", "America/New_York", "America/Chicago",
        "America/Denver", "America/Los_Angeles");
    private static final ChoiceOrder BY_OFFSET = new ChoiceOrder("по смещению UTC", (values, at) -> {
        final Map<String, Integer> offsets = new java.util.HashMap<String, Integer>();
        for (String value : values) {
            offsets.put(value, offsetSeconds(value, at));
        }
        List<String> sorted = new ArrayList<String>(values);
        Collections.sort(sorted, Comparator.<String, Integer>comparing(offsets::get).thenComparing(Comparator.<String>naturalOrder()));
        return sorted;
    });

    /**
     * Time zones a person would look for: {@code Europe/Moscow}, {@code America/New_York} and {@code UTC}. Legacy
     * aliases ({@code US/Pacific}, {@code SystemV/AST4}) and the {@code Etc/GMT+N} series are left out; they only make
     * the list longer. Each zone tells the time there now and its offset from UTC; the order is by that offset, then by
     * name, or by alphabet.
     */
    public static final ChoiceSource ZONES = zones();

    private final String id;
    private final List<String> all;
    private final List<ChoiceOrder> orders;
    private final List<String> popular;
    private final List<String> cycle;
    private final BiFunction<String, Instant, List<Pair<String, String>>> details;

    /** A source with only the alphabetical order and no details. */
    public ChoiceSource(@NotNull String id, @NotNull List<String> all) {
        this(id, all, Collections.singletonList(ALPHABET), Collections.<String>emptyList(),
            Collections.<String>emptyList(), (value, at) -> Collections.<Pair<String, String>>emptyList());
    }

    /** A source with only the alphabetical order, whose {@code curated} values are shown first. */
    public ChoiceSource(@NotNull String id, @NotNull List<String> all, @NotNull List<String> curated) {
        this(id, all, Collections.singletonList(ALPHABET), curated, Collections.<String>emptyList(),
            (value, at) -> Collections.<Pair<String, String>>emptyList());
    }

    /**
     * Creates a source.
     *
     * @param id the name sent over the link and used by the menu to find the list again
     * @param all every value, alphabetically and without repeats
     * @param orders the ways to sort the list; the first one is shown at first
     * @param curated the values worth showing first, in the order they are shown; values not in {@code all} are dropped
     * @param cycled the values a click on the menu button rolls through, in their order; empty means every value
     * @param details lines {@code label to value} that tell about a value, so it can be picked without knowing its name
     */
    public ChoiceSource(@NotNull String id, @NotNull List<String> all, @NotNull List<ChoiceOrder> orders,
                        @NotNull List<String> curated, @NotNull List<String> cycled,
                        @NotNull BiFunction<String, Instant, List<Pair<String, String>>> details) {
        this.id = id;
        this.all = Collections.unmodifiableList(new ArrayList<String>(all));
        this.orders = Collections.unmodifiableList(new ArrayList<ChoiceOrder>(orders));
        this.popular = Collections.unmodifiableList(existing(curated, all));
        List<String> rolled = existing(cycled, all);
        this.cycle = Collections.unmodifiableList(rolled.isEmpty() ? new ArrayList<String>(all) : rolled);
        this.details = details;
    }

    /** The source named {@code id}, or null when there is none. */
    public static @Nullable ChoiceSource named(@NotNull String id) {
        return ZONE_ID.equals(id) ? ZONES : null;
    }

    /** The offset of {@code zone} from UTC at the moment {@code at}, in seconds, summer time included. */
    public static int offsetSeconds(@NotNull String zone, @NotNull Instant at) {
        return ZoneId.of(zone).getRules().getOffset(at).getTotalSeconds();
    }

    /** An offset as {@code UTC}, {@code UTC+03:00} or {@code UTC-05:00}. */
    public static @NotNull String offsetText(int seconds) {
        if (seconds == 0) {
            return "UTC";
        }
        String sign = seconds < 0 ? "-" : "+";
        int minutes = Math.abs(seconds) % SECONDS_IN_HOUR / SECONDS_IN_MINUTE;
        return String.format(Locale.ROOT, "UTC%s%02d:%02d", sign, Math.abs(seconds) / SECONDS_IN_HOUR, minutes);
    }

    /** The time of day in {@code zone} at the moment {@code at}, as {@code 21:45}. */
    public static @NotNull String localTime(@NotNull String zone, @NotNull Instant at) {
        return at.atZone(ZoneId.of(zone)).format(CLOCK);
    }

    /**
     * Whether {@code value} contains {@code query}, ignoring the case and the difference between {@code _},
     * {@code /} and a space, so that {@code new york} finds {@code America/New_York}.
     */
    public static boolean matches(@NotNull String value, @NotNull String query) {
        return normalize(value).contains(normalize(query));
    }

    /** The name sent over the link and used by the menu to find the list again. */
    public @NotNull String getId() {
        return id;
    }

    /** Every value, alphabetically and without repeats. */
    public @NotNull List<String> getAll() {
        return all;
    }

    /** The ways to sort the list; the first one is shown at first. */
    public @NotNull List<ChoiceOrder> getOrders() {
        return orders;
    }

    /** The curated values, in their own order. */
    public @NotNull List<String> getPopular() {
        return popular;
    }

    /** The values a click rolls through: the cycled ones that exist, or all values. */
    public @NotNull List<String> getCycle() {
        return cycle;
    }

    /**
     * The values the rolling switcher shows for the {@code current} one: the cycle, with {@code current} put first
     * when it is not in it (a zone nobody listed as popular is still shown, as the value in use).
     */
    public @NotNull List<String> rolling(@NotNull String current) {
        if (cycle.contains(current)) {
            return cycle;
        }
        List<String> shown = new ArrayList<String>();
        shown.add(current);
        shown.addAll(cycle);
        return shown;
    }

    /** The value after {@code current} in the cycle, wrapping around; a value outside the cycle moves to the first. */
    public @NotNull String next(@NotNull String current) {
        int at = cycle.indexOf(current);
        return at >= 0 ? cycle.get(Math.floorMod(at + 1, cycle.size())) : cycle.get(0);
    }

    /** The value before {@code current} in the cycle, wrapping around; a value outside the cycle moves to the last. */
    public @NotNull String previous(@NotNull String current) {
        int at = cycle.indexOf(current);
        return at >= 0 ? cycle.get(Math.floorMod(at - 1, cycle.size())) : cycle.get(cycle.size() - 1);
    }

    /** The lore lines of {@code value} as they are now. */
    public @NotNull List<Pair<String, String>> lore(@NotNull String value) {
        return lore(value, Instant.now());
    }

    /** The lore lines of {@code value} as they are at the moment {@code at}. */
    public @NotNull List<Pair<String, String>> lore(@NotNull String value, @NotNull Instant at) {
        return details.apply(value, at);
    }

    /** What the picker shows at this moment; see {@link #view(int, boolean, String, Instant)}. */
    public @NotNull List<String> view(int order, boolean onlyPopular, @NotNull String filter) {
        return view(order, onlyPopular, filter, Instant.now());
    }

    /**
     * What the picker shows: the values that contain {@code filter} (see {@link #matches}) sorted by the order number
     * {@code order}, or, with no filter, either the popular ones or everything.
     */
    public @NotNull List<String> view(int order, boolean onlyPopular, @NotNull String filter, @NotNull Instant at) {
        int index = Math.max(0, Math.min(order, orders.size() - 1));
        List<String> sorted = orders.get(index).sort(all, at);
        if (!filter.trim().isEmpty()) {
            List<String> found = new ArrayList<String>();
            for (String value : sorted) {
                if (matches(value, filter)) {
                    found.add(value);
                }
            }
            return found;
        }
        return onlyPopular ? popular : sorted;
    }

    private static ChoiceSource zones() {
        TreeSet<String> ids = new TreeSet<String>();
        for (String zone : ZoneId.getAvailableZoneIds()) {
            if (EXTRA.contains(zone) || inRegion(zone)) {
                ids.add(zone);
            }
        }
        return new ChoiceSource(ZONE_ID, new ArrayList<String>(ids), Arrays.asList(BY_OFFSET, ALPHABET),
            POPULAR_ZONES, Collections.<String>emptyList(), (zone, at) -> Arrays.asList(
                new Pair<String, String>("Смещение", offsetText(offsetSeconds(zone, at))),
                new Pair<String, String>("Сейчас там", localTime(zone, at))));
    }

    private static boolean inRegion(String zone) {
        for (String region : REGIONS) {
            if (zone.startsWith(region + "/")) {
                return true;
            }
        }
        return false;
    }

    private static List<String> existing(List<String> wanted, List<String> all) {
        List<String> kept = new ArrayList<String>();
        for (String value : wanted) {
            if (all.contains(value)) {
                kept.add(value);
            }
        }
        return kept;
    }

    private static String normalize(String text) {
        String lowered = text.toLowerCase(Locale.ROOT).replace('_', ' ').replace('/', ' ').trim();
        return BLANKS.matcher(lowered).replaceAll(" ");
    }
}
