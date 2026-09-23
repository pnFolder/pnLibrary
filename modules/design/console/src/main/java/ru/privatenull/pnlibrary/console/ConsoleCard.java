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

    public static Builder builder(ConsoleTheme theme, String title) { return new Builder(theme, title); }

    public List<String> render() {
        List<String> result = new ArrayList<String>();
        result.add("");
        result.add(theme.border + "          ━━━━━━━━━━━ " + title + " ━━━━━━━━━━━" + theme.reset);
        result.addAll(lines);
        result.add(theme.border + "          ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + theme.reset);
        result.add("");
        return Collections.unmodifiableList(result);
    }

    public void send(ConsoleSink sink) {
        if (sink == null) throw new IllegalArgumentException("sink is required");
        for (String line : render()) sink.send(line);
    }

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

        public Builder mascot(String face, String headline) {
            return mascot(face, headline, null);
        }

        public Builder mascot(String face, String headline, String subtitle) {
            lines.add(theme.accent + " /\\_/\\" + theme.reset);
            lines.add(theme.accent + face + "     " + theme.text + text(headline) + theme.reset);
            lines.add(theme.accent + " > ^ <" + theme.reset + (subtitle == null ? "" : "      " + theme.muted + subtitle + theme.reset));
            return this;
        }

        public Builder blank() { lines.add(""); return this; }

        public Builder detail(String label, Object value) {
            lines.add(theme.muted + "            ├ " + theme.text + pad(label, 13)
                    + theme.accent + String.valueOf(value) + theme.reset);
            return this;
        }

        public Builder lastDetail(String label, Object value) {
            lines.add(theme.muted + "            └ " + theme.text + pad(label, 13)
                    + theme.accent + String.valueOf(value) + theme.reset);
            return this;
        }

        public Builder section(String title) {
            lines.add(theme.muted + "            ◆ " + theme.text + text(title) + theme.reset);
            return this;
        }

        public Builder item(String value) {
            lines.add(theme.muted + "              ├ " + text(value) + theme.reset);
            return this;
        }

        public Builder lastItem(String value) {
            lines.add(theme.muted + "              └ " + text(value) + theme.reset);
            return this;
        }

        public Builder status(String value) {
            lines.add(theme.accent + "          ■ " + text(value) + theme.reset);
            return this;
        }

        public ConsoleCard build() { return new ConsoleCard(this); }

        private static String text(String value) {
            if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("text must not be blank");
            return value;
        }

        private static String pad(String value, int width) {
            String checked = text(value);
            StringBuilder result = new StringBuilder(checked);
            while (result.length() < width) result.append(' ');
            return result.toString();
        }
    }
}
