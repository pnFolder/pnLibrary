package ru.privatenull.pnlibrary.compat;

import org.bukkit.Bukkit;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Immutable Minecraft version detected once from the active Bukkit server. */
public record ServerVersion(int major, int minor, int patch) implements Comparable<ServerVersion> {

    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?");
    private static final ServerVersion CURRENT = parse(Bukkit.getBukkitVersion());

    public static ServerVersion current() {
        return CURRENT;
    }

    public static ServerVersion parse(String value) {
        Matcher matcher = VERSION_PATTERN.matcher(value == null ? "" : value);
        if (!matcher.find()) return new ServerVersion(0, 0, 0);
        return new ServerVersion(
                number(matcher.group(1)),
                number(matcher.group(2)),
                number(matcher.group(3))
        );
    }

    public boolean isAtLeast(int targetMajor, int targetMinor, int targetPatch) {
        return compareTo(new ServerVersion(targetMajor, targetMinor, targetPatch)) >= 0;
    }

    public boolean isBefore(int targetMajor, int targetMinor, int targetPatch) {
        return compareTo(new ServerVersion(targetMajor, targetMinor, targetPatch)) < 0;
    }

    public boolean isReleaseLine(int targetMajor, int targetMinor) {
        return major == targetMajor && minor == targetMinor;
    }

    public boolean isKnown() {
        return major > 0;
    }

    @Override
    public int compareTo(ServerVersion other) {
        int majorResult = Integer.compare(major, other.major);
        if (majorResult != 0) return majorResult;
        int minorResult = Integer.compare(minor, other.minor);
        return minorResult != 0 ? minorResult : Integer.compare(patch, other.patch);
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }

    private static int number(String raw) {
        if (raw == null || raw.isBlank()) return 0;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
