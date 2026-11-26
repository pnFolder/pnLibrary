package org.pnfolder.pnlibrary.core.diagnostics.redaction;

import java.util.Objects;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.diagnostics.report.DiagnosticLevel;

/**
 * Converts untrusted diagnostic values into bounded, redacted report data.
 *
 * <p>Plugin contributors may return recursive containers, oversized strings, or arbitrary objects. This class
 * centralizes the limits applied at that trust boundary so state storage never retains raw plugin objects or
 * exceptions.
 */
public final class DiagnosticValueSanitizer {
    private static final int MAX_KEY_LENGTH = 96;
    private static final int MAX_FIELD_NAME_LENGTH = 128;
    private static final int MAX_VALUE_LENGTH = 4_096;
    private static final int MAX_EXCEPTION_CHARS = 1_048_576;
    private static final int MAX_DEPTH = 8;
    private static final int MAX_CONTAINER_ENTRIES = 128;
    private static final Pattern SECRET_KEY_PATTERN = Pattern.compile(
        "(?i).*(?:password|passwd|pwd|secret|token|api[-_ ]?key|authorization|cookie|private[-_ ]?key|credential).*");

    private final DiagnosticRedactor redactor;

    /** Creates a sanitizer with the built-in redactor. */
    public DiagnosticValueSanitizer() {
        this(new DiagnosticRedactor());
    }

    /** Creates a sanitizer that redacts text with {@code redactor}. */
    public DiagnosticValueSanitizer(@NotNull DiagnosticRedactor redactor) {
        this.redactor = Objects.requireNonNull(redactor, "redactor");
    }

    /** Returns a bounded, redacted copy of {@code values}; secret-looking keys keep no value. */
    @NotNull
    public Map<String, Object> map(@Nullable Map<?, ?> values) {
        return map(values, 0, new IdentityHashMap<>());
    }

    /** Redacts {@code value} and cuts it to {@code maximumLength} characters; {@code null} reads as empty text. */
    @NotNull
    public String text(@Nullable String value, int maximumLength) {
        String redacted = redactor.redact(value == null ? "" : value);
        return redacted.length() <= maximumLength ? redacted : redacted.substring(0, maximumLength);
    }

    /** Normalizes a plugin or component identifier for case-insensitive lookup. */
    @NotNull
    public String key(@Nullable String value) {
        String normalized = value == null ? "" : Whitespace.trim(value).toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return "unknown";
        }
        return normalized.length() <= MAX_KEY_LENGTH ? normalized : normalized.substring(0, MAX_KEY_LENGTH);
    }

    /** Formats, redacts, and bounds an exception without retaining the throwable. */
    @NotNull
    public String exception(@NotNull Throwable error) {
        StringWriter writer = new StringWriter();
        error.printStackTrace(new PrintWriter(writer));
        String complete = redactor.redactStackTrace(writer.toString());
        if (complete.length() <= MAX_EXCEPTION_CHARS) {
            return complete;
        }
        return complete.substring(0, MAX_EXCEPTION_CHARS)
            + "\n[TRUNCATED: throwable exceeded the hard " + MAX_EXCEPTION_CHARS + "-character safety limit]";
    }

    /** Produces a stable short fingerprint for equivalent diagnostic incidents. */
    @NotNull
    public String incidentId(@NotNull String plugin, @NotNull DiagnosticLevel level, @NotNull String component,
                             @NotNull String code, @NotNull String message, @Nullable Throwable error) {
        List<String> context = new ArrayList<>();
        context.add(key(plugin));
        context.add(level.name());
        context.add(component);
        context.add(code);
        return DiagnosticThrowables.fingerprint(error, context, message);
    }

    /** Returns the first useful application frame for an exception, if available. */
    @Nullable
    public Map<String, Object> exceptionOrigin(@NotNull Throwable error) {
        return DiagnosticThrowables.originData(error);
    }

    private Map<String, Object> map(Map<?, ?> values, int depth, IdentityHashMap<Object, Boolean> seen) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (values == null) {
            return result;
        }
        if (depth >= MAX_DEPTH || seen.put(values, Boolean.TRUE) != null) {
            result.put("limit", "[recursive/depth limit]");
            return result;
        }
        int taken = 0;
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            if (taken++ >= MAX_CONTAINER_ENTRIES) {
                break;
            }
            String key = text(entry.getKey() == null ? null : entry.getKey().toString(), MAX_FIELD_NAME_LENGTH);
            boolean secret = SECRET_KEY_PATTERN.matcher(key).matches();
            result.put(key, secret ? "[REDACTED]" : value(entry.getValue(), depth + 1, seen));
        }
        seen.remove(values);
        return result;
    }

    private Object value(Object value, int depth, IdentityHashMap<Object, Boolean> seen) {
        if (value == null || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (depth >= MAX_DEPTH) {
            return "[depth limit]";
        }
        if (value instanceof Map) {
            return map((Map<?, ?>) value, depth, seen);
        }
        if (value instanceof Iterable) {
            return iterable((Iterable<?>) value, depth, seen);
        }
        return text(value.toString(), MAX_VALUE_LENGTH);
    }

    private Object iterable(Iterable<?> values, int depth, IdentityHashMap<Object, Boolean> seen) {
        if (seen.put(values, Boolean.TRUE) != null) {
            return "[recursive reference]";
        }
        List<Object> result = new ArrayList<>();
        for (Object item : values) {
            if (result.size() >= MAX_CONTAINER_ENTRIES) {
                break;
            }
            result.add(value(item, depth + 1, seen));
        }
        seen.remove(values);
        return result;
    }
}
