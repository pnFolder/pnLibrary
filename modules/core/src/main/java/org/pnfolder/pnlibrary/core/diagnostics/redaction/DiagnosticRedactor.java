package org.pnfolder.pnlibrary.core.diagnostics.redaction;

import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Best-effort text redactor for diagnostic data.
 *
 * <p>Applied eagerly to every string value that enters the system. Strips or replaces: credential lines, URLs,
 * IPv4/IPv6, e-mails, UUIDs, file paths and ANSI/section-sign control sequences.
 *
 * <p>This is a defence-in-depth layer: callers should not rely on it as the only secret-protection mechanism.
 */
public final class DiagnosticRedactor {
    private static final Pattern SECRET_LINE = Pattern.compile(
        "(?im)^.*(?:password|passwd|pwd|secret|token|api[-_ ]?key|authorization|"
            + "cookie|private[-_ ]?key)[\\\"']?\\s*[:=].*$");
    private static final Pattern URL = Pattern.compile(
        "(?i)\\b(?:https?|jdbc|mysql|postgresql|mongodb(?:\\+srv)?|rediss?)://[^\\s<>]+"
            + "|\\bjdbc:[^\\s<>]+");
    private static final Pattern IPV4 = Pattern.compile(
        "(?<![\\w.])(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d{1,5})?(?![\\w.])");
    private static final Pattern IPV6 = Pattern.compile(
        "(?i)(?<![\\w:])(?=[a-f0-9:]*::|(?:[a-f0-9]{1,4}:){3})(?:[a-f0-9]{0,4}:){2,}[a-f0-9:.]*(?:%[\\w]+)?");
    private static final Pattern EMAIL = Pattern.compile("(?<![\\w.+-])[\\w.+-]++@[\\w.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PATH = Pattern.compile("(?i)(?:[a-z]:[\\\\/]|(?<![\\w:])/)(?:[^\\s<>]+)");
    private static final Pattern MORE_FRAMES = Pattern.compile("\\.\\.\\. \\d+ more");
    private static final Pattern LINE_BREAK = Pattern.compile("\r\n|\n|\r");
    private static final Pattern CONTROL = Pattern.compile(
        "\\u001B\\[[0-?]*[ -/]*[@-~]|§[0-9a-fk-orx]|[\\p{Cc}&&[^\\n\\t]]",
        Pattern.CASE_INSENSITIVE);

    /** Redacts every sensitive value in {@code input}; {@code null} reads as empty text. */
    @NotNull
    public String redact(@Nullable String input) {
        return redactCommon(input, true);
    }

    /** Redacts sensitive values without mistaking Java stack-frame JAR notation for a local path. */
    @NotNull
    public String redactStackTrace(@NotNull String input) {
        String[] lines = LINE_BREAK.split(input, -1);
        StringBuilder result = new StringBuilder(input.length());
        for (int index = 0; index < lines.length; index++) {
            String trimmed = Whitespace.trimStart(lines[index]);
            boolean frame = trimmed.startsWith("at ") || MORE_FRAMES.matcher(trimmed).matches();
            if (index > 0) {
                result.append('\n');
            }
            result.append(redactCommon(lines[index], !frame));
        }
        return result.toString();
    }

    private static String redactCommon(String input, boolean redactPaths) {
        String value = CONTROL.matcher(input == null ? "" : input).replaceAll("");
        value = SECRET_LINE.matcher(value).replaceAll("[REDACTED: credential line]");
        value = URL.matcher(value).replaceAll("[REDACTED: URL]");
        value = EMAIL.matcher(value).replaceAll("[REDACTED: email]");
        value = DiagnosticThrowables.UUID_PATTERN.matcher(value).replaceAll("[REDACTED: UUID]");
        value = IPV4.matcher(value).replaceAll("[REDACTED: address]");
        value = IPV6.matcher(value).replaceAll("[REDACTED: address]");
        return redactPaths ? PATH.matcher(value).replaceAll("[REDACTED: path]") : value;
    }
}
