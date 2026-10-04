package ru.privatenull.pnlibrary.console;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable, consistently formatted status card for plugin consoles. */
public final class ConsoleCard {
    private final ConsoleTheme theme;
    private final String title;
    private final List<String> lines;

    private ConsoleCard(Builder builder) {
        this.theme = builder.theme;
        this.title = builder.title;
        this.lines = Collections.unmodifiableList(new ArrayList<String>(builder.lines));
    }

    /**
     * Creates a card builder.
     *
     * @param theme platform-specific presentation theme
     * @param title card title
     * @return a new card builder
     */
    public static Builder builder(ConsoleTheme theme, String title) { return new Builder(theme, title); }

    /** Renders this card.
     * @return immutable, fully formatted lines ready for a console sink */
    public List<String> render() {
        List<String> result = new ArrayList<String>();
        result.add("");
        result.add(theme.border + "          ━━━━━━━━━━━ " + title + " ━━━━━━━━━━━" + theme.reset);
        result.addAll(lines);
        result.add(theme.border + "          ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + theme.reset);
        result.add("");
        return Collections.unmodifiableList(result);
    }

    /** Sends this card.
     * @param sink destination that receives every rendered line */
    public void send(ConsoleSink sink) {
        if (sink == null) throw new IllegalArgumentException("sink is required");
        for (String line : render()) sink.send(line);
    }

    /** Fluent builder for consistently formatted console cards. */
    public static final class Builder {
        private final ConsoleTheme theme;
        private final String title;
        private final List<String> lines = new ArrayList<String>();

        private Builder(ConsoleTheme theme, String title) {
            if (theme == null) throw new IllegalArgumentException("theme is required");
            if (title == null || title.trim().isEmpty()) throw new IllegalArgumentException("title is required");
            this.theme = theme;
            this.title = title.trim();
        }

        /**
         * Adds a mascot with a headline and no subtitle.
         *
         * @param face mascot face
         * @param headline primary mascot text
         * @return this builder
         */
        public Builder mascot(String face, String headline) {
            return mascot(face, headline, null);
        }

        /**
         * Adds a mascot block.
         *
         * @param face mascot face
         * @param headline primary mascot text
         * @param subtitle optional secondary text
         * @return this builder
         */
        public Builder mascot(String face, String headline, String subtitle) {
            String normalizedFace = text(face).trim();
            if (!(normalizedFace.startsWith("(") && normalizedFace.endsWith(")"))) {
                normalizedFace = "( " + normalizedFace + " )";
            }
            lines.add(theme.accent + " /\\_/\\" + theme.reset);
            lines.add(theme.accent + normalizedFace + "     " + theme.text + text(headline) + theme.reset);
            lines.add(theme.accent + " > ^ <" + theme.reset + (subtitle == null ? "" : "      " + theme.muted + subtitle + theme.reset));
            return this;
        }

        /** Adds a blank separator line.
         * @return this builder after adding a blank separator line */
        public Builder blank() {
            lines.add("");
            return this;
        }

        /**
         * Adds the first row of a connected detail group.
         *
         * @param label row label
         * @param value row value
         * @return this builder
         */
        public Builder firstDetail(String label, Object value) {
            lines.add(theme.muted + "            ┌ " + theme.text + pad(label, 22)
                    + theme.accent + String.valueOf(value) + theme.reset);
            return this;
        }

        /**
         * Adds a middle row to a connected detail group.
         *
         * @param label row label
         * @param value row value
         * @return this builder
         */
        public Builder detail(String label, Object value) {
            lines.add(theme.muted + "            ├ " + theme.text + pad(label, 22)
                    + theme.accent + String.valueOf(value) + theme.reset);
            return this;
        }

        /**
         * Adds the final row of a connected detail group.
         *
         * @param label row label
         * @param value row value
         * @return this builder
         */
        public Builder lastDetail(String label, Object value) {
            lines.add(theme.muted + "            └ " + theme.text + pad(label, 22)
                    + theme.accent + String.valueOf(value) + theme.reset);
            return this;
        }

        /**
         * Adds a branch-style section heading.
         *
         * @param title section title
         * @return this builder
         */
        public Builder section(String title) {
            lines.add(theme.muted + "            ┌ " + theme.text + text(title) + theme.reset);
            return this;
        }

        /**
         * Adds a centered divider.
         *
         * @param title divider title
         * @return this builder
         */
        public Builder divider(String title) {
            lines.add(theme.muted + "            ───────── " + theme.text + text(title)
                    + theme.muted + " ─────────" + theme.reset);
            return this;
        }

        /**
         * Adds a middle item to the current section.
         *
         * @param value item text
         * @return this builder
         */
        public Builder item(String value) {
            lines.add(theme.muted + "              ├ " + text(value) + theme.reset);
            return this;
        }

        /**
         * Adds the final item to the current section.
         *
         * @param value item text
         * @return this builder
         */
        public Builder lastItem(String value) {
            lines.add(theme.muted + "              └ " + text(value) + theme.reset);
            return this;
        }

        /**
         * Adds a nested explanation tree with stable branch connectors.
         *
         * @param tree immutable explanation tree
         * @return this builder
         */
        public Builder tree(ConsoleTree tree) {
            if (tree == null) throw new IllegalArgumentException("tree is required");
            appendTree(tree, "", true, true);
            return this;
        }

        /**
         * Adds the emphasized status line at the bottom of the card.
         *
         * @param value status text
         * @return this builder
         */
        public Builder status(String value) {
            lines.add(theme.accent + "          ■ " + text(value) + theme.reset);
            return this;
        }

        /** Builds the configured card.
         * @return the immutable console card represented by this builder */
        public ConsoleCard build() { return new ConsoleCard(this); }

        private void appendTree(ConsoleTree node, String prefix, boolean last, boolean root) {
            String connector = root ? "┌ " : (last ? "└ " : "├ ");
            lines.add(theme.muted + "            " + prefix + connector + theme.text + node.text() + theme.reset);
            List<ConsoleTree> children = node.children();
            for (int index = 0; index < children.size(); index++) {
                appendTree(children.get(index), prefix + (root ? "  " : (last ? "  " : "│ ")),
                        index == children.size() - 1, false);
            }
        }

        private static String text(String value) {
            if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("text must not be blank");
            return value;
        }

        private static String pad(String value, int width) {
            String checked = text(value);
            StringBuilder result = new StringBuilder(checked);
            while (result.length() < width) result.append(' ');
            if (result.length() >= width) result.append("  ");
            return result.toString();
        }
    }
}
