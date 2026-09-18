package ru.privatenull.pnlibrary.core.diagnostics

import java.util.regex.Pattern

/**
 * Best-effort text redactor for diagnostic data.
 *
 * Applied eagerly to every string value that enters the system.
 * Strips or replaces: credential lines, URLs, IPv4/IPv6, e-mails, UUIDs,
 * file paths and ANSI/§ control sequences.
 *
 * This is a defence-in-depth layer — callers should not rely on it as the
 * only secret-protection mechanism.
 */
internal class DiagnosticRedactor {

    fun redact(input: String?): String {
        return redactCommon(input, redactPaths = true)
    }

    /** Redacts sensitive values without mistaking Java stack-frame JAR notation for a local path. */
    fun redactStackTrace(input: String): String = input.lineSequence().joinToString("\n") { line ->
        val trimmed = line.trimStart()
        redactCommon(line, redactPaths = !trimmed.startsWith("at ") && !trimmed.matches(MORE_FRAMES))
    }

    private fun redactCommon(input: String?, redactPaths: Boolean): String {
        var v = CONTROL.matcher(input ?: "").replaceAll("")
        v = SECRET_LINE.matcher(v).replaceAll("[REDACTED: credential line]")
        v = URL.matcher(v).replaceAll("[REDACTED: URL]")
        v = EMAIL.matcher(v).replaceAll("[REDACTED: email]")
        v = UUID_PAT.matcher(v).replaceAll("[REDACTED: UUID]")
        v = IPV4.matcher(v).replaceAll("[REDACTED: address]")
        v = IPV6.matcher(v).replaceAll("[REDACTED: address]")
        return if (redactPaths) PATH.matcher(v).replaceAll("[REDACTED: path]") else v
    }

    companion object {
        private val SECRET_LINE: Pattern = Pattern.compile(
            "(?im)^.*(?:password|passwd|pwd|secret|token|api[-_ ]?key|authorization|" +
                "cookie|private[-_ ]?key)[\\\"']?\\s*[:=].*$"
        )
        private val URL: Pattern = Pattern.compile(
            "(?i)\\b(?:https?|jdbc|mysql|postgresql|mongodb(?:\\+srv)?|rediss?)://[^\\s<>]+" +
                "|\\bjdbc:[^\\s<>]+"
        )
        private val IPV4: Pattern = Pattern.compile(
            "(?<![\\w.])(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d{1,5})?(?![\\w.])"
        )
        private val IPV6: Pattern = Pattern.compile(
            "(?i)(?<![\\w:])(?=[a-f0-9:]*::|(?:[a-f0-9]{1,4}:){3})(?:[a-f0-9]{0,4}:){2,}[a-f0-9:.]*(?:%[\\w]+)?"
        )
        private val EMAIL: Pattern = Pattern.compile(
            "(?<![\\w.+-])[\\w.+-]++@[\\w.-]+\\.[A-Za-z]{2,}"
        )
        private val UUID_PAT: Pattern = Pattern.compile(
            "(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b"
        )
        private val PATH: Pattern = Pattern.compile(
            "(?i)(?:[a-z]:[\\\\/]|(?<![\\w:])/)(?:[^\\s<>]+)"
        )
        private val MORE_FRAMES = Regex("\\.\\.\\. \\d+ more")
        private val CONTROL: Pattern = Pattern.compile(
            "\\u001B\\[[0-?]*[ -/]*[@-~]|§[0-9a-fk-orx]|[\\p{Cc}&&[^\\n\\t]]",
            Pattern.CASE_INSENSITIVE
        )
    }
}
