package ru.privatenull.pnlibrary.console;

/** Color/style tokens supplied by a platform adapter. Empty tokens produce plain text. */
public final class ConsoleTheme {
    /** Style applied to card borders and tree connectors. */
    public final String border;
    /** Style applied to emphasized values and status text. */
    public final String accent;
    /** Style applied to ordinary labels and content. */
    public final String text;
    /** Style applied to secondary content. */
    public final String muted;
    /** Sequence that restores the platform's default style. */
    public final String reset;

    /**
     * Creates a theme from platform-specific style sequences.
     *
     * @param border border style
     * @param accent emphasized-value style
     * @param text ordinary text style
     * @param muted secondary text style
     * @param reset style reset sequence
     */
    public ConsoleTheme(String border, String accent, String text, String muted, String reset) {
        this.border = value(border);
        this.accent = value(accent);
        this.text = value(text);
        this.muted = value(muted);
        this.reset = value(reset);
    }

    /** Creates an unstyled theme.
     * @return a theme that emits no color or formatting sequences */
    public static ConsoleTheme plain() { return new ConsoleTheme("", "", "", "", ""); }

    private static String value(String value) { return value == null ? "" : value; }
}
