package org.pnfolder.pnlibrary.bootstrap;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Semantic version comparison that works before pnLibrary or a Kotlin runtime is installed. */
public final class BootstrapVersion {
    private static final Pattern VERSION = Pattern.compile(
            "(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)"
                    + "(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?"
                    + "(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?"
    );

    private BootstrapVersion() {}

    /**
     * Validates [value] as a semantic version and removes a leading {@code v}.
     *
     * @param value version such as {@code 1.2.0}, {@code v1.2.0} or {@code 1.2.0-beta.1}
     * @return the normalized version
     * @throws IllegalArgumentException when the value is not a semantic version
     */
    public static String normalize(String value) {
        if (value == null) throw new IllegalArgumentException("version is required");
        String normalized = value.trim();
        if (normalized.startsWith("v") || normalized.startsWith("V")) normalized = normalized.substring(1);
        Matcher match = VERSION.matcher(normalized);
        if (!match.matches()) {
            throw new IllegalArgumentException("invalid semantic version: " + value);
        }
        if (match.group(4) != null) {
            for (String identifier : match.group(4).split("\\.")) {
                if (numeric(identifier) && identifier.length() > 1 && identifier.startsWith("0")) {
                    throw new IllegalArgumentException("invalid semantic version: " + value);
                }
            }
        }
        return normalized;
    }

    /**
     * Compares two semantic versions; a pre-release is older than its release and build metadata is ignored.
     *
     * @param actual version that is present
     * @param required lowest acceptable version
     * @return {@code true} when {@code actual} is {@code required} or newer
     */
    public static boolean isAtLeast(String actual, String required) {
        String[] left = parts(actual);
        String[] right = parts(required);
        String[] leftCore = left[0].split("\\.");
        String[] rightCore = right[0].split("\\.");
        for (int i = 0; i < 3; i++) {
            int comparison = compareNumeric(leftCore[i], rightCore[i]);
            if (comparison != 0) return comparison > 0;
        }
        if (left.length == 1) return true;
        if (right.length == 1) return false;
        String[] leftPre = left[1].split("\\.");
        String[] rightPre = right[1].split("\\.");
        for (int i = 0; i < Math.min(leftPre.length, rightPre.length); i++) {
            int comparison = compareIdentifier(leftPre[i], rightPre[i]);
            if (comparison != 0) return comparison > 0;
        }
        return leftPre.length >= rightPre.length;
    }

    private static String[] parts(String value) {
        return normalize(value).split("\\+", 2)[0].split("-", 2);
    }

    private static int compareIdentifier(String left, String right) {
        boolean leftNumeric = numeric(left);
        boolean rightNumeric = numeric(right);
        if (leftNumeric && rightNumeric) return compareNumeric(left, right);
        if (leftNumeric != rightNumeric) return leftNumeric ? -1 : 1;
        return rankWord(left).compareTo(rankWord(right));
    }

    /** The word {@code dev} is the least mature channel, so it sorts before every other word. */
    private static String rankWord(String word) {
        return "dev".equals(word) ? "" : word;
    }

    private static int compareNumeric(String left, String right) {
        int length = Integer.compare(left.length(), right.length());
        return length == 0 ? left.compareTo(right) : length;
    }

    private static boolean numeric(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) < '0' || value.charAt(i) > '9') return false;
        }
        return true;
    }
}
