package org.pnfolder.pnlibrary.config.yaml;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.jetbrains.annotations.NotNull;

/**
 * Cleans up the support note that older versions of pnLibrary wrote into every configuration file.
 *
 * <p>The note (a Russian line, an English line and one or more link lines) is no longer written: help links live in the
 * generated files of the library's own folder. A file that still carries the note loses it the next time it is written.
 * Nothing but the note lines is touched.
 */
final class LegacySupportNote {
    private static final List<String> OLD_PREFIXES = Arrays.asList(
            "# Если у вас возникли проблемы с плагином",
            "# If you have any problem with a plugin");
    private static final Pattern LINK_LINE = Pattern.compile("^# (\\S+: )?https?://\\S+$");

    private LegacySupportNote() {
    }

    /** Returns {@code content} without any old support note; content without a note is returned unchanged. */
    @NotNull
    static String removeFrom(@NotNull String content) {
        List<String> kept = new ArrayList<>();
        boolean inNote = false;
        for (String line : content.split("\n", -1)) {
            boolean isNote = startsWithOld(line) || (inNote && LINK_LINE.matcher(line).matches());
            inNote = isNote;
            if (!isNote) {
                kept.add(line);
            }
        }
        return String.join("\n", kept);
    }

    private static boolean startsWithOld(String line) {
        for (String prefix : OLD_PREFIXES) {
            if (line.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
