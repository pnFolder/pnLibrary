package org.pnfolder.pnlibrary.common.minecraft;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Known Minecraft game version exposed by the Bukkit-specific API.
 *
 * <p>Comparisons use {@link #getMajor()}, {@link #getMinor()}, and {@link #getPatch()}, never {@link Enum#ordinal()}.
 * Adding constants therefore cannot change existing ordering. {@link #UNKNOWN} represents a release that has not been
 * added to the library yet.
 *
 * <pre>{@code
 * MinecraftVersion version = MinecraftVersion.parse("1.21.11");
 * if (version.isAtLeast(MinecraftVersion.V1_20_5)) {
 *     enableComponents();
 * }
 * }</pre>
 */
public enum MinecraftVersion {
    V1_8("1.8", 1, 8, 0), V1_8_1("1.8.1", 1, 8, 1), V1_8_2("1.8.2", 1, 8, 2),
    V1_8_3("1.8.3", 1, 8, 3), V1_8_4("1.8.4", 1, 8, 4), V1_8_5("1.8.5", 1, 8, 5),
    V1_8_6("1.8.6", 1, 8, 6), V1_8_7("1.8.7", 1, 8, 7), V1_8_8("1.8.8", 1, 8, 8),
    V1_8_9("1.8.9", 1, 8, 9),
    V1_9("1.9", 1, 9, 0), V1_9_1("1.9.1", 1, 9, 1), V1_9_2("1.9.2", 1, 9, 2),
    V1_9_3("1.9.3", 1, 9, 3), V1_9_4("1.9.4", 1, 9, 4),
    V1_10("1.10", 1, 10, 0), V1_10_1("1.10.1", 1, 10, 1), V1_10_2("1.10.2", 1, 10, 2),
    V1_11("1.11", 1, 11, 0), V1_11_1("1.11.1", 1, 11, 1), V1_11_2("1.11.2", 1, 11, 2),
    V1_12("1.12", 1, 12, 0), V1_12_1("1.12.1", 1, 12, 1), V1_12_2("1.12.2", 1, 12, 2),
    V1_13("1.13", 1, 13, 0), V1_13_1("1.13.1", 1, 13, 1), V1_13_2("1.13.2", 1, 13, 2),
    V1_14("1.14", 1, 14, 0), V1_14_1("1.14.1", 1, 14, 1), V1_14_2("1.14.2", 1, 14, 2),
    V1_14_3("1.14.3", 1, 14, 3), V1_14_4("1.14.4", 1, 14, 4),
    V1_15("1.15", 1, 15, 0), V1_15_1("1.15.1", 1, 15, 1), V1_15_2("1.15.2", 1, 15, 2),
    V1_16("1.16", 1, 16, 0), V1_16_1("1.16.1", 1, 16, 1), V1_16_2("1.16.2", 1, 16, 2),
    V1_16_3("1.16.3", 1, 16, 3), V1_16_4("1.16.4", 1, 16, 4), V1_16_5("1.16.5", 1, 16, 5),
    V1_17("1.17", 1, 17, 0), V1_17_1("1.17.1", 1, 17, 1),
    V1_18("1.18", 1, 18, 0), V1_18_1("1.18.1", 1, 18, 1), V1_18_2("1.18.2", 1, 18, 2),
    V1_19("1.19", 1, 19, 0), V1_19_1("1.19.1", 1, 19, 1), V1_19_2("1.19.2", 1, 19, 2),
    V1_19_3("1.19.3", 1, 19, 3), V1_19_4("1.19.4", 1, 19, 4),
    V1_20("1.20", 1, 20, 0), V1_20_1("1.20.1", 1, 20, 1), V1_20_2("1.20.2", 1, 20, 2),
    V1_20_3("1.20.3", 1, 20, 3), V1_20_4("1.20.4", 1, 20, 4), V1_20_5("1.20.5", 1, 20, 5),
    V1_20_6("1.20.6", 1, 20, 6),
    V1_21("1.21", 1, 21, 0), V1_21_1("1.21.1", 1, 21, 1), V1_21_2("1.21.2", 1, 21, 2),
    V1_21_3("1.21.3", 1, 21, 3), V1_21_4("1.21.4", 1, 21, 4), V1_21_5("1.21.5", 1, 21, 5),
    V1_21_6("1.21.6", 1, 21, 6), V1_21_7("1.21.7", 1, 21, 7), V1_21_8("1.21.8", 1, 21, 8),
    V1_21_9("1.21.9", 1, 21, 9), V1_21_10("1.21.10", 1, 21, 10), V1_21_11("1.21.11", 1, 21, 11),
    V26_1("26.1", 26, 1, 0), V26_2("26.2", 26, 2, 0),
    UNKNOWN("unknown", -1, -1, -1);

    private static final Pattern VERSION_PATTERN = Pattern.compile("(?<!\\d)(\\d+)\\.(\\d+)(?:\\.(\\d+))?");
    private static final Map<String, MinecraftVersion> BY_NUMBERS = byNumbers();
    private static final List<MinecraftVersion> KNOWN_NEWEST_FIRST = knownNewestFirst();

    private final String text;
    private final int major;
    private final int minor;
    private final int patch;
    private final long coordinates;

    MinecraftVersion(String text, int major, int minor, int patch) {
        this.text = text;
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.coordinates = major * 1_000_000L + minor * 1_000L + patch;
    }

    /** Canonical representation, for example {@code 1.21.11}. */
    @NotNull
    public String getText() {
        return text;
    }

    /** First numeric component. */
    public int getMajor() {
        return major;
    }

    /** Second numeric component. */
    public int getMinor() {
        return minor;
    }

    /** Third numeric component, or {@code 0} when omitted. */
    public int getPatch() {
        return patch;
    }

    /** {@code false} only for {@link #UNKNOWN}. */
    public boolean isKnown() {
        return this != UNKNOWN;
    }

    /** Returns whether this version is equal to or newer than {@code other}. */
    public boolean isAtLeast(@NotNull MinecraftVersion other) {
        return isKnown() && other.isKnown() && coordinates >= other.coordinates;
    }

    /** Returns whether this version is equal to or older than {@code other}. */
    public boolean isAtMost(@NotNull MinecraftVersion other) {
        return isKnown() && other.isKnown() && coordinates <= other.coordinates;
    }

    /** Strict comparison: this version must be newer than {@code other}. */
    public boolean isNewerThan(@NotNull MinecraftVersion other) {
        return isKnown() && other.isKnown() && coordinates > other.coordinates;
    }

    /** Strict comparison: this version must be older than {@code other}. */
    public boolean isOlderThan(@NotNull MinecraftVersion other) {
        return isKnown() && other.isKnown() && coordinates < other.coordinates;
    }

    /** Readable alias for {@link #isAtLeast(MinecraftVersion)}. */
    public boolean isSameOrNewerThan(@NotNull MinecraftVersion other) {
        return isAtLeast(other);
    }

    /** Readable alias for {@link #isAtMost(MinecraftVersion)}. */
    public boolean isSameOrOlderThan(@NotNull MinecraftVersion other) {
        return isAtMost(other);
    }

    /**
     * Checks whether both versions belong to the same release line, ignoring patch. For example, {@code 1.20.4} and
     * {@code 1.20.6} share the {@code 1.20} line.
     */
    public boolean isSameReleaseLine(@NotNull MinecraftVersion other) {
        return isKnown() && other.isKnown() && major == other.major && minor == other.minor;
    }

    /**
     * Checks the inclusive range from {@code minimum} to {@code maximum}.
     *
     * <pre>{@code
     * boolean legacy = version.isBetween(MinecraftVersion.V1_8_8, MinecraftVersion.V1_12_2);
     * }</pre>
     */
    public boolean isBetween(@NotNull MinecraftVersion minimum, @NotNull MinecraftVersion maximum) {
        return isAtLeast(minimum) && isAtMost(maximum);
    }

    /**
     * Returns whether this version belongs to {@code range}.
     *
     * <pre>{@code
     * if (version.inRange(MinecraftVersionRange.atLeast(MinecraftVersion.V1_20_5))) enableModernApi();
     * }</pre>
     */
    public boolean inRange(@NotNull MinecraftVersionRange range) {
        return range.contains(this);
    }

    /** Every version represented by this pnLibrary release, newest first. */
    @NotNull
    public static List<MinecraftVersion> supported() {
        return KNOWN_NEWEST_FIRST;
    }

    /**
     * Finds the first {@code number.number[.number]} version in {@code value}.
     *
     * <p>Supports strings such as {@code 1.20.6-R0.1-SNAPSHOT}, {@code git-Paper-123 (MC: 1.21.11)}, and
     * {@code 26.2-112-c9e894d}. Returns {@link #UNKNOWN} for empty, malformed, or not-yet-listed releases.
     */
    @NotNull
    public static MinecraftVersion parse(@Nullable String value) {
        return parseInfo(value).getParsed();
    }

    /** Parses {@code value} without discarding an unknown or future numeric release. */
    @NotNull
    public static MinecraftVersionInfo parseInfo(@Nullable String value) {
        String raw = value == null ? "" : value;
        Matcher match = VERSION_PATTERN.matcher(raw);
        if (!match.find()) {
            return new MinecraftVersionInfo(raw, UNKNOWN, null, null, null);
        }
        Integer major = parseIntOrNull(match.group(1));
        Integer minor = parseIntOrNull(match.group(2));
        Integer parsedPatch = parseIntOrNull(match.group(3));
        if (major == null || minor == null) {
            return new MinecraftVersionInfo(raw, UNKNOWN, null, null, null);
        }
        int patch = parsedPatch == null ? 0 : parsedPatch;
        MinecraftVersion known = BY_NUMBERS.get(numbersKey(major, minor, patch));
        return new MinecraftVersionInfo(raw, known == null ? UNKNOWN : known, major, minor, patch);
    }

    /** Creates an inclusive range from {@code minimum} to {@code maximum}. */
    @NotNull
    public static MinecraftVersionRange range(@NotNull MinecraftVersion minimum, @NotNull MinecraftVersion maximum) {
        return MinecraftVersionRange.between(minimum, maximum);
    }

    /** Creates {@code [minimum, +∞)}. */
    @NotNull
    public static MinecraftVersionRange atLeast(@NotNull MinecraftVersion minimum) {
        return MinecraftVersionRange.atLeast(minimum);
    }

    /** Creates {@code (-∞, maximum]}. */
    @NotNull
    public static MinecraftVersionRange atMost(@NotNull MinecraftVersion maximum) {
        return MinecraftVersionRange.atMost(maximum);
    }

    private long coordinates() {
        return coordinates;
    }

    private static Integer parseIntOrNull(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException tooLarge) {
            return null;
        }
    }

    private static String numbersKey(int major, int minor, int patch) {
        return major + "." + minor + "." + patch;
    }

    private static Map<String, MinecraftVersion> byNumbers() {
        Map<String, MinecraftVersion> byNumbers = new HashMap<>();
        for (MinecraftVersion version : values()) {
            if (version.isKnown()) {
                byNumbers.put(numbersKey(version.major, version.minor, version.patch), version);
            }
        }
        return byNumbers;
    }

    private static List<MinecraftVersion> knownNewestFirst() {
        List<MinecraftVersion> known = new ArrayList<>();
        for (MinecraftVersion version : values()) {
            if (version.isKnown()) {
                known.add(version);
            }
        }
        known.sort(Comparator.comparingLong((MinecraftVersion version) -> version.coordinates()).reversed());
        return Collections.unmodifiableList(known);
    }
}
