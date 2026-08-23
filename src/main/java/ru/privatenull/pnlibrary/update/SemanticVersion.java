package ru.privatenull.pnlibrary.update;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Нестрогое представление SemVer, используемое только проверкой обновлений. */
record SemanticVersion(int major, int minor, int patch, List<String> prerelease)
        implements Comparable<SemanticVersion> {

    SemanticVersion {
        prerelease = List.copyOf(prerelease);
    }

    static SemanticVersion parse(String input) {
        String value = normalize(input);
        int buildIndex = value.indexOf('+');
        if (buildIndex >= 0) value = value.substring(0, buildIndex);

        List<String> prerelease = List.of();
        int prereleaseIndex = value.indexOf('-');
        if (prereleaseIndex >= 0) {
            prerelease = identifiers(value.substring(prereleaseIndex + 1));
            value = value.substring(0, prereleaseIndex);
        }

        String[] parts = value.split("\\.");
        return new SemanticVersion(number(parts, 0), number(parts, 1), number(parts, 2), prerelease);
    }

    static String normalize(String input) {
        if (input == null) return "";
        String value = input.trim();
        return value.startsWith("v") || value.startsWith("V") ? value.substring(1) : value;
    }

    @Override
    public int compareTo(SemanticVersion other) {
        int result = Integer.compare(major, other.major);
        if (result == 0) result = Integer.compare(minor, other.minor);
        if (result == 0) result = Integer.compare(patch, other.patch);
        if (result != 0) return result;

        if (prerelease.isEmpty() && other.prerelease.isEmpty()) return 0;
        if (prerelease.isEmpty()) return 1;
        if (other.prerelease.isEmpty()) return -1;

        int size = Math.max(prerelease.size(), other.prerelease.size());
        for (int index = 0; index < size; index++) {
            if (index >= prerelease.size()) return -1;
            if (index >= other.prerelease.size()) return 1;
            result = compareIdentifier(prerelease.get(index), other.prerelease.get(index));
            if (result != 0) return result;
        }
        return 0;
    }

    private static int compareIdentifier(String left, String right) {
        boolean leftNumeric = left.matches("\\d+");
        boolean rightNumeric = right.matches("\\d+");
        if (leftNumeric && rightNumeric) return compareNumericText(left, right);
        if (leftNumeric) return -1;
        if (rightNumeric) return 1;
        return left.compareToIgnoreCase(right);
    }

    private static int compareNumericText(String left, String right) {
        String normalizedLeft = left.replaceFirst("^0+(?!$)", "");
        String normalizedRight = right.replaceFirst("^0+(?!$)", "");
        int length = Integer.compare(normalizedLeft.length(), normalizedRight.length());
        return length != 0 ? length : normalizedLeft.compareTo(normalizedRight);
    }

    private static List<String> identifiers(String value) {
        if (value.isBlank()) return List.of();
        List<String> result = new ArrayList<>();
        Collections.addAll(result, value.split("\\."));
        return result;
    }

    private static int number(String[] parts, int index) {
        if (index >= parts.length) return 0;
        String numericPrefix = parts[index].replaceFirst("[^0-9].*$", "");
        if (numericPrefix.isEmpty()) return 0;
        try {
            return Integer.parseInt(numericPrefix);
        } catch (NumberFormatException ignored) {
            return Integer.MAX_VALUE;
        }
    }
}
