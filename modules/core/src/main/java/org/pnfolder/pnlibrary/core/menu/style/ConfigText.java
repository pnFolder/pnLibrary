package org.pnfolder.pnlibrary.core.menu.style;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Breaks long values into short lines under their label, so a value never runs off the item. A value is broken at
 * spaces and after {@code / \ . - _ :}, so a path or a class name is cut between its parts and never inside a word.
 */
public final class ConfigText {
    private static final int LINE_WIDTH = 38;
    private static final int LABEL_FRAME = 4;
    private static final int CONTINUATION_FRAME = 5;
    private static final int MIN_WIDTH = 12;
    private static final int FACT_LINES = 5;
    private static final String BREAKS = " /\\.-_:,";

    private ConfigText() {
    }

    /** One fact; a long value continues on the lines below the label. */
    public static @NotNull List<Fact> fact(@NotNull String label, @NotNull String value) {
        List<String> lines = softWrap(value, LINE_WIDTH - label.length() - LABEL_FRAME, LINE_WIDTH - CONTINUATION_FRAME);
        List<String> kept = new ArrayList<>(lines);
        if (lines.size() > FACT_LINES) {
            kept = new ArrayList<>(lines.subList(0, FACT_LINES - 1));
            kept.add(lines.get(FACT_LINES - 1) + "…");
        }
        List<Fact> facts = new ArrayList<>();
        for (int index = 0; index < kept.size(); index++) {
            facts.add(new Fact(index == 0 ? label : "", kept.get(index)));
        }
        return facts;
    }

    /** Breaks {@code text} into lines that fit {@code firstWidth} and then {@code width}; a piece longer than a line is cut only as the last resort. */
    public static @NotNull List<String> softWrap(@NotNull String text, int firstWidth, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        int limit = Math.max(firstWidth, MIN_WIDTH);
        for (String piece : pieces(text)) {
            if (line.length() > 0 && line.length() + trimEnd(piece).length() > limit) {
                lines.add(trimEnd(line.toString()));
                line = new StringBuilder();
                limit = width;
            }
            String rest = piece;
            while (trimEnd(rest).length() > limit) {
                if (line.length() > 0) {
                    lines.add(trimEnd(line.toString()));
                    line = new StringBuilder();
                    limit = width;
                }
                lines.add(rest.substring(0, limit));
                rest = rest.substring(limit);
                limit = width;
            }
            line.append(rest);
        }
        if (line.length() > 0 || lines.isEmpty()) {
            lines.add(trimEnd(line.toString()));
        }
        return lines;
    }

    private static List<String> pieces(String text) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int index = 0; index < text.length(); index++) {
            char symbol = text.charAt(index);
            current.append(symbol);
            if (BREAKS.indexOf(symbol) >= 0) {
                result.add(current.toString());
                current.setLength(0);
            }
        }
        if (current.length() > 0) {
            result.add(current.toString());
        }
        return result;
    }

    private static String trimEnd(String text) {
        int end = text.length();
        while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end);
    }
}
