package org.pnfolder.pnlibrary.core.menu.style;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/** Shared text styles for menu item names and descriptions. */
public final class Style {
    /** The remark after the option that is the default one. */
    public static final String DEFAULT_NOTE = "(по умолчанию)";

    private static final String DIM = MenuColors.color("&#64546E");
    private static final String PLAIN = MenuColors.color("&#F7F2F7");
    private static final String DESCRIPTION = MenuColors.color(" &#E9DDF2");
    private static final String SUB = MenuColors.color("   &#8B7A99↳ &#F7F2F7");
    private static final String[] TAGS = {"D19BFF", "5BC8F5", "6EE7A0", "FFB347", "FF9BD2", "FDE047", "B79CFF"};

    private Style() {
    }

    public static @NotNull String name(@NotNull String label, @NotNull Tone tone) {
        return MenuColors.gradient(label, tone.getFrom(), tone.getTo());
    }

    private static String click(String label, String from, String to, String action) {
        return DIM + " ▸ " + MenuColors.gradient(label, from, to) + "  " + PLAIN + action;
    }

    public static @NotNull String left(@NotNull String action) {
        return click("Клик", "D19BFF", "F5C6FF", action);
    }

    public static @NotNull String right(@NotNull String action) {
        return click("Правый клик", "FF9BD2", "FF7A9B", action);
    }

    public static @NotNull String shiftLeft(@NotNull String action) {
        return click("Shift + клик", "5BC8F5", "B5ECFF", action);
    }

    public static @NotNull String middle(@NotNull String action) {
        return click("Колёсико", "FDE047", "FFB347", action);
    }

    public static @NotNull String description(@NotNull String text) {
        return DESCRIPTION + text;
    }

    /** A small grey italic remark, for example {@code (по умолчанию)} after an option; the one place that turns italics on. */
    public static @NotNull String note(@NotNull String text) {
        return MenuColors.color("&#9AA4AD") + MenuColors.legacy('o') + text;
    }

    /** The option list of a switch or a choice, with no option marked as the default. */
    public static @NotNull List<String> choices(@NotNull List<String> options, int selected) {
        return choices(options, selected, -1);
    }

    /** The option list of a switch or a choice; the option at {@code defaultIndex} gets the grey italic mark. */
    public static @NotNull List<String> choices(@NotNull List<String> options, int selected, int defaultIndex) {
        List<String> lines = new ArrayList<>();
        for (int index = 0; index < options.size(); index++) {
            String label = options.get(index);
            String line = index == selected
                ? MenuColors.color("   &#6EE7A0➤ ") + MenuColors.gradient(label, "D19BFF", "FF9BD2")
                : MenuColors.color("   &#64546E· &#8B7A99") + label;
            lines.add(index == defaultIndex ? line + " " + note(DEFAULT_NOTE) : line);
        }
        return lines;
    }

    public static @NotNull String danger(@NotNull String text) {
        return MenuColors.color(" &#FF7A7A") + text;
    }

    /** A bracketed label such as {@code [ Версия ]}, in a colour that stays the same for the same word. */
    public static @NotNull String tag(@NotNull String label) {
        String color = TAGS[Math.floorMod(label.hashCode(), TAGS.length)];
        return DIM + " [ " + MenuColors.color("&#" + color) + label + " " + DIM + "] " + PLAIN;
    }

    /** A description with only click hints. */
    public static @NotNull List<String> lore(@NotNull List<String> lines, @NotNull List<Fact> parameters,
                                             @NotNull String... hints) {
        return lore(lines, parameters, Collections.<String>emptyList(), hints);
    }

    /**
     * Description lines, a blank line, {@code [ label ] value} lines (an empty label continues the line above with an
     * indented {@code ↳ value}), the extra lines, a blank line, then the click hints; framed by blank lines.
     */
    public static @NotNull List<String> lore(@NotNull List<String> lines, @NotNull List<Fact> parameters,
                                             @NotNull List<String> extra, @NotNull String... hints) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        for (String line : lines) {
            lore.add(description(line));
        }
        if (!lines.isEmpty() && !parameters.isEmpty()) {
            lore.add("");
        }
        for (Fact fact : parameters) {
            lore.add(fact.getLabel().isEmpty() ? SUB + fact.getValue() : tag(fact.getLabel()) + fact.getValue());
        }
        if (!extra.isEmpty()) {
            lore.add("");
            lore.addAll(extra);
        }
        if (hints.length > 0) {
            lore.add("");
            lore.addAll(Arrays.asList(hints));
        }
        lore.add("");
        return lore;
    }
}
