package ru.privatenull.pnlibrary.console;

/** Color/style tokens supplied by a platform adapter. Empty tokens produce plain text. */
public final class ConsoleTheme {
    public final String border;
    public final String accent;
    public final String text;
    public final String muted;
    public final String reset;

    public ConsoleTheme(String border, String accent, String text, String muted, String reset) {
        this.border = value(border);
        this.accent = value(accent);
        this.text = value(text);
        this.muted = value(muted);
        this.reset = value(reset);
    }

    public static ConsoleTheme plain() { return new ConsoleTheme("", "", "", "", ""); }

    private static String value(String value) { return value == null ? "" : value; }
}
