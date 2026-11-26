package org.pnfolder.pnlibrary.api.scenario.requirements.builtin;

/** Substring tests that may ignore case, as {@link CompareOperator} needs them. */
final class TextMatching {
    private TextMatching() {
    }

    static boolean contains(String text, String part, boolean ignoreCase) {
        if (!ignoreCase) {
            return text.contains(part);
        }
        for (int i = 0; i + part.length() <= text.length(); i++) {
            if (text.regionMatches(true, i, part, 0, part.length())) {
                return true;
            }
        }
        return false;
    }

    static boolean startsWith(String text, String prefix, boolean ignoreCase) {
        return text.regionMatches(ignoreCase, 0, prefix, 0, prefix.length());
    }

    static boolean endsWith(String text, String suffix, boolean ignoreCase) {
        return text.regionMatches(ignoreCase, text.length() - suffix.length(), suffix, 0, suffix.length());
    }
}
