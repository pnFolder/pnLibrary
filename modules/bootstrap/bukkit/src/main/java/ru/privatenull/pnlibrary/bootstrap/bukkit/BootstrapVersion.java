package ru.privatenull.pnlibrary.bootstrap.bukkit;

final class BootstrapVersion {
    private BootstrapVersion() {}

    static String normalize(String value) {
        if (value == null) throw new IllegalArgumentException("version is required");
        String normalized = value.trim();
        if (normalized.startsWith("v") || normalized.startsWith("V")) normalized = normalized.substring(1);
        if (!normalized.matches("\\d+\\.\\d+\\.\\d+(?:[-+][0-9A-Za-z.-]+)?")) {
            throw new IllegalArgumentException("invalid semantic version: " + value);
        }
        return normalized;
    }

    static boolean isAtLeast(String actual, String required) {
        int[] left = core(normalize(actual));
        int[] right = core(normalize(required));
        for (int i = 0; i < 3; i++) {
            if (left[i] != right[i]) return left[i] > right[i];
        }
        boolean leftPre = normalize(actual).contains("-");
        boolean rightPre = normalize(required).contains("-");
        return !leftPre || rightPre;
    }

    private static int[] core(String value) {
        String[] values = value.split("[-+]", 2)[0].split("\\.");
        return new int[] { Integer.parseInt(values[0]), Integer.parseInt(values[1]), Integer.parseInt(values[2]) };
    }
}
