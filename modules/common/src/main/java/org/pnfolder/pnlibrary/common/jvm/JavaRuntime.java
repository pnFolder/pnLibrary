package org.pnfolder.pnlibrary.common.jvm;

import org.jetbrains.annotations.Nullable;

/** Runtime Java information available on every JVM supported by pnLibrary. */
public final class JavaRuntime {
    private static final int MINIMUM = 8;

    private JavaRuntime() {
    }

    /** Java feature release, such as 8, 17 or 25; unknown values fall back to the minimum, 8. */
    public static int getFeature() {
        return parseFeature(System.getProperty("java.specification.version"));
    }

    static int parseFeature(@Nullable String value) {
        if (value == null) {
            return MINIMUM;
        }
        String version = value.startsWith("1.") ? value.substring(2) : value;
        version = cutAt(version, '.');
        version = cutAt(version, '-');
        Integer feature = parseOrNull(version);
        return feature != null && feature >= MINIMUM ? feature : MINIMUM;
    }

    private static String cutAt(String text, char delimiter) {
        int index = text.indexOf(delimiter);
        return index < 0 ? text : text.substring(0, index);
    }

    private static Integer parseOrNull(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }
}
