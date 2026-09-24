package ru.privatenull.pnlibrary.compatibility.bukkit;

final class CompatibilityVersion {
    private CompatibilityVersion() {}

    static String normalize(String value) {
        if (value == null) throw new IllegalArgumentException("version is required");
        String result = value.trim();
        if (result.startsWith("v") || result.startsWith("V")) result = result.substring(1);
        if (!result.matches("\\d+\\.\\d+\\.\\d+(?:[-+][0-9A-Za-z.-]+)?")) {
            throw new IllegalArgumentException("invalid semantic version: " + value);
        }
        return result;
    }

    static boolean accepts(String installed, String minimum) {
        String left = normalize(installed);
        String right = normalize(minimum);
        int[] a = core(left);
        int[] b = core(right);
        for (int i = 0; i < 3; i++) if (a[i] != b[i]) return a[i] > b[i];
        return !left.contains("-") || right.contains("-");
    }

    private static int[] core(String value) {
        String[] values = value.split("[-+]", 2)[0].split("\\.");
        return new int[] { Integer.parseInt(values[0]), Integer.parseInt(values[1]), Integer.parseInt(values[2]) };
    }
}
