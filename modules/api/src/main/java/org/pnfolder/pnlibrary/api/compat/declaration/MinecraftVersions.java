package org.pnfolder.pnlibrary.api.compat.declaration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.compat.MinecraftSupport;
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion;
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersionInfo;

/**
 * The Minecraft versions a plugin or module runs on. Build it with {@link #from(String)}, {@link #between(String, String)},
 * {@link #upTo(String)} or {@link #only(String...)}, or take a ready one ({@link #ANY}, {@link #LEGACY},
 * {@link #MODERN}), and join several with {@link #plus}: the server must fit at least one part.
 *
 * <p>Versions are compared by their numbers, never by a list of known releases, so a release pnLibrary has not heard
 * of yet still compares correctly: {@code from("1.16.5")} accepts it. Give a version as a {@link MinecraftVersion}
 * constant or as text, for a release that has no constant yet.
 *
 * <pre>{@code
 * MinecraftVersions versions = MinecraftVersions.LEGACY.plus(MinecraftVersions.from("1.16.5"));
 * setup.supportedMinecraft(versions);
 * versions.accepts("1.14.4"); // false: 1.14.4 is between the two parts
 * }</pre>
 */
public final class MinecraftVersions {
    /** Nothing declared: every version is fine, and a module takes its plugin's declaration. */
    public static final MinecraftVersions NONE = new MinecraftVersions(Collections.<Range>emptyList());

    /** Every version, declared on purpose. */
    public static final MinecraftVersions ANY = new MinecraftVersions(Collections.singletonList(new Range(null, null)));

    /** The versions before the 1.13 flattening: 1.12.2 and older. */
    public static final MinecraftVersions LEGACY = upTo(MinecraftVersion.V1_12_2);

    /** The versions since the 1.13 flattening: 1.13 and newer, also releases pnLibrary does not know yet. */
    public static final MinecraftVersions MODERN = from(MinecraftVersion.V1_13);

    private final List<Range> ranges;

    private MinecraftVersions(List<Range> ranges) {
        this.ranges = Collections.unmodifiableList(new ArrayList<Range>(ranges));
    }

    /** The parts, each from a version to a version; a {@code null} side is open. Empty means nothing was declared. */
    public @NotNull List<Range> getRanges() {
        return ranges;
    }

    /** Whether any version was declared. */
    public boolean isDeclared() {
        return !ranges.isEmpty();
    }

    /** The declared versions in words, such as {@code 1.16.5-1.21.4, 1.8.8}, or {@code null} when none. */
    public @Nullable String getDescription() {
        if (ranges.isEmpty()) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        for (Range range : ranges) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(describe(range));
        }
        return text.toString();
    }

    private static String describe(Range range) {
        String low = range.getMinimum();
        String high = range.getMaximum();
        if (low != null && high != null) {
            return compare(number(low), number(high)) == 0 ? low : low + "-" + high;
        }
        if (low != null) {
            return low + "+";
        }
        return high != null ? "до " + high : "любая";
    }

    /** These versions and {@code other} together. */
    public @NotNull MinecraftVersions plus(@NotNull MinecraftVersions other) {
        List<Range> joined = new ArrayList<Range>(ranges);
        joined.addAll(other.ranges);
        return new MinecraftVersions(joined);
    }

    /** Whether the server's {@code version} is inside the declared versions, or nothing was declared. */
    public boolean accepts(@NotNull MinecraftVersion version) {
        return accepts(version.getText());
    }

    /** Whether the server's {@code version}, written as text, is inside the declared versions, or nothing was declared. */
    public boolean accepts(@NotNull String version) {
        MinecraftSupport support = check(MinecraftVersion.parseInfo(version));
        return support == MinecraftSupport.SUPPORTED || support == MinecraftSupport.NOT_DECLARED;
    }

    /** Where the server's {@code version} stands; {@link MinecraftSupport#UNKNOWN} when it has no numbers. */
    public @NotNull MinecraftSupport check(@Nullable MinecraftVersionInfo version) {
        if (ranges.isEmpty()) {
            return MinecraftSupport.NOT_DECLARED;
        }
        if (version == null || !version.isNumeric()) {
            return MinecraftSupport.UNKNOWN;
        }
        int[] at = {version.getMajor(), version.getMinor(), version.getPatch() == null ? 0 : version.getPatch()};
        for (Range range : ranges) {
            if (fits(at, range)) {
                return MinecraftSupport.SUPPORTED;
            }
        }
        int[] highest = bound(true);
        int[] lowest = bound(false);
        if (highest != null && compare(at, highest) > 0) {
            return MinecraftSupport.NEWER_THAN_DECLARED;
        }
        if (lowest != null && compare(at, lowest) < 0) {
            return MinecraftSupport.OLDER_THAN_DECLARED;
        }
        return MinecraftSupport.EXCLUDED;
    }

    /** The newest maximum or the oldest minimum of all parts, or {@code null} when any part is open on that side. */
    private int[] bound(boolean newest) {
        int[] result = null;
        for (Range range : ranges) {
            String side = newest ? range.getMaximum() : range.getMinimum();
            if (side == null) {
                return null;
            }
            int[] number = number(side);
            if (result == null || (newest ? compare(number, result) > 0 : compare(number, result) < 0)) {
                result = number;
            }
        }
        return result;
    }

    private static boolean fits(int[] at, Range range) {
        return (range.getMinimum() == null || compare(at, number(range.getMinimum())) >= 0)
            && (range.getMaximum() == null || compare(at, number(range.getMaximum())) <= 0);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MinecraftVersions && ((MinecraftVersions) other).ranges.equals(ranges);
    }

    @Override
    public int hashCode() {
        return ranges.hashCode();
    }

    @Override
    public String toString() {
        String description = getDescription();
        return description != null ? description : "not declared";
    }

    /** {@code version} and every newer one, also releases pnLibrary does not know yet. */
    public static @NotNull MinecraftVersions from(@NotNull MinecraftVersion version) {
        return from(text(version));
    }

    /** {@code version}, written as text, and every newer one. */
    public static @NotNull MinecraftVersions from(@NotNull String version) {
        return between(version, (String) null);
    }

    /** Every version up to {@code version}, included. */
    public static @NotNull MinecraftVersions upTo(@NotNull MinecraftVersion version) {
        return upTo(text(version));
    }

    /** Every version up to {@code version}, written as text, included. */
    public static @NotNull MinecraftVersions upTo(@NotNull String version) {
        return between((String) null, version);
    }

    /** The versions from {@code from} to {@code to}, both included. */
    public static @NotNull MinecraftVersions between(@NotNull MinecraftVersion from, @NotNull MinecraftVersion to) {
        return between(text(from), text(to));
    }

    /** The versions from {@code from} to {@code to}, written as text, both included; a {@code null} side is open. */
    public static @NotNull MinecraftVersions between(@Nullable String from, @Nullable String to) {
        int[] low = from == null ? null : number(from);
        int[] high = to == null ? null : number(to);
        if (low != null && high != null && compare(low, high) > 0) {
            throw new IllegalArgumentException("The oldest version " + from + " is newer than the newest " + to);
        }
        return new MinecraftVersions(Collections.singletonList(new Range(from, to)));
    }

    /** Exactly these versions and nothing between them. */
    public static @NotNull MinecraftVersions only(@NotNull MinecraftVersion... versions) {
        String[] texts = new String[versions.length];
        for (int index = 0; index < versions.length; index++) {
            texts[index] = text(versions[index]);
        }
        return only(texts);
    }

    /** Exactly these versions, written as text, and nothing between them. */
    public static @NotNull MinecraftVersions only(@NotNull String... versions) {
        if (versions.length == 0) {
            throw new IllegalArgumentException("Give at least one version");
        }
        MinecraftVersions joined = NONE;
        for (String version : versions) {
            joined = joined.plus(between(version, version));
        }
        return joined;
    }

    private static String text(MinecraftVersion version) {
        if (version == MinecraftVersion.UNKNOWN) {
            throw new IllegalArgumentException("UNKNOWN is not a Minecraft version; give the version as text");
        }
        return version.getText();
    }

    private static int[] number(String text) {
        MinecraftVersionInfo info = MinecraftVersion.parseInfo(text);
        if (!info.isNumeric()) {
            throw new IllegalArgumentException("Not a Minecraft version: " + text);
        }
        return new int[] {info.getMajor(), info.getMinor(), info.getPatch() == null ? 0 : info.getPatch()};
    }

    private static int compare(int[] a, int[] b) {
        for (int index = 0; index < 3; index++) {
            int result = Integer.compare(a[index], b[index]);
            if (result != 0) {
                return result;
            }
        }
        return 0;
    }

    /** One part: {@link #getMinimum()} to {@link #getMaximum()} included, written as text such as {@code 1.16.5}. */
    public static final class Range {
        private final String minimum;
        private final String maximum;

        /**
         * @param minimum the oldest version, or {@code null} when open
         * @param maximum the newest version, or {@code null} when open
         */
        public Range(@Nullable String minimum, @Nullable String maximum) {
            this.minimum = minimum;
            this.maximum = maximum;
        }

        /** The oldest version, or {@code null} when open. */
        public @Nullable String getMinimum() {
            return minimum;
        }

        /** The newest version, or {@code null} when open. */
        public @Nullable String getMaximum() {
            return maximum;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Range)) {
                return false;
            }
            Range that = (Range) other;
            return Objects.equals(minimum, that.minimum) && Objects.equals(maximum, that.maximum);
        }

        @Override
        public int hashCode() {
            return Objects.hash(minimum, maximum);
        }

        @Override
        public String toString() {
            return "Range(minimum=" + minimum + ", maximum=" + maximum + ")";
        }
    }
}
