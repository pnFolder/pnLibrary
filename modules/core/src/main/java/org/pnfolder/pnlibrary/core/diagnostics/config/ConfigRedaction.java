package org.pnfolder.pnlibrary.core.diagnostics.config;

import java.util.Objects;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.core.diagnostics.redaction.DiagnosticRedactor;
import org.pnfolder.pnlibrary.core.diagnostics.redaction.Whitespace;

/**
 * The values of one configuration file that a report must hide.
 *
 * <p>{@link #requested(Map)} is what an administrator (key and value patterns) or the plugin
 * ({@code @ConfigSecret}, its own patterns) asked for; those promises hold in every report. {@link #redact(Map)}
 * adds the built-in secret key names, the built-in text redactor and size limits for a plaintext report.
 */
public final class ConfigRedaction {
    private static final int MAX_DEPTH = 10;
    private static final int MAX_ENTRIES_PER_MAP = 200;
    private static final int MAX_ENTRIES_PER_LIST = 100;
    private static final int MAX_PATTERN_LENGTH = 256;
    private static final String EXCLUDED = "[EXCLUDED BY RULE]";
    private static final String SECRET_KEY = "[REDACTED SECRET KEY]";
    private static final String SECRET_VALUE = "[REDACTED VALUE]";
    private static final Pattern BUILT_IN_SECRET_KEY = Pattern.compile(
        "(?i).*(?:password|passwd|pwd|secret|token|api[-_ ]?key|authorization|cookie|private[-_ ]?key|credential|webhook|mysql|auth|jdbc).*");

    private final Set<String> excludedPaths;
    private final List<Pattern> keyPatterns;
    private final List<Pattern> valuePatterns;
    private final DiagnosticRedactor redactor;
    private final List<String[]> wildcardPaths = new ArrayList<>();

    /** Creates the rules of one file: paths and key patterns to hide, value patterns to blank. */
    public ConfigRedaction(@NotNull Set<String> excludedPaths, @NotNull List<Pattern> keyPatterns,
                           @NotNull List<Pattern> valuePatterns, @NotNull DiagnosticRedactor redactor) {
        this.excludedPaths = Objects.requireNonNull(excludedPaths, "excludedPaths");
        this.keyPatterns = Objects.requireNonNull(keyPatterns, "keyPatterns");
        this.valuePatterns = Objects.requireNonNull(valuePatterns, "valuePatterns");
        this.redactor = Objects.requireNonNull(redactor, "redactor");
        for (String path : excludedPaths) {
            String[] segments = segments(path);
            if (contains(segments, "*")) {
                wildcardPaths.add(segments);
            }
        }
    }

    /** Compiles case-insensitive patterns; an invalid one is skipped so one typo cannot block every report. */
    @NotNull
    public static List<Pattern> compile(@NotNull List<String> patterns) {
        List<Pattern> compiled = new ArrayList<>();
        for (final String source : patterns) {
            if (Whitespace.isBlank(source) || source.length() > MAX_PATTERN_LENGTH) {
                continue;
            }
            Pattern pattern = Failures.runCatchingNonFatal(() -> Pattern.compile(source, Pattern.CASE_INSENSITIVE))
                .getOrNull();
            if (pattern != null) {
                compiled.add(pattern);
            }
        }
        return compiled;
    }

    /** Whether anything was asked to be hidden in this file. */
    public boolean hasRequests() {
        return !excludedPaths.isEmpty() || !keyPatterns.isEmpty() || !valuePatterns.isEmpty();
    }

    /** Hides only the requested values; everything else stays exactly as parsed. */
    @NotNull
    public Map<String, Object> requested(@NotNull Map<String, Object> data) {
        return hideRequested(data, "");
    }

    /** Hides the requested values, every built-in secret and redactable text, within the plaintext size limits. */
    @NotNull
    public Map<String, Object> redact(@NotNull Map<String, Object> data) {
        return redactTree(data, "", 0);
    }

    private Map<String, Object> hideRequested(Map<String, Object> data, String parent) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            String path = parent.isEmpty() ? key : parent + "." + key;
            if (isExcluded(path)) {
                result.put(key, EXCLUDED);
            } else if (matchesKeyPattern(key, path)) {
                result.put(key, SECRET_KEY);
            } else {
                result.put(key, hideRequestedValue(entry.getValue(), path));
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Object hideRequestedValue(Object value, String path) {
        if (value instanceof Map) {
            return hideRequested((Map<String, Object>) value, path);
        }
        if (value instanceof List) {
            List<Object> items = new ArrayList<>();
            for (Object item : (List<Object>) value) {
                items.add(hideRequestedValue(item, path));
            }
            return items;
        }
        if (value == null || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        return replaceValuePatterns(value.toString());
    }

    private Map<String, Object> redactTree(Map<String, Object> data, String parent, int depth) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (depth >= MAX_DEPTH) {
            result.put("[limit]", "[depth limit reached]");
            return result;
        }
        int taken = 0;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (taken++ >= MAX_ENTRIES_PER_MAP) {
                break;
            }
            String key = entry.getKey();
            String path = parent.isEmpty() ? key : parent + "." + key;
            if (isExcluded(path)) {
                result.put(key, EXCLUDED);
            } else if (isBuiltInSecret(key, path) || matchesKeyPattern(key, path)) {
                result.put(key, SECRET_KEY);
            } else {
                result.put(key, redactValue(entry.getValue(), path, depth));
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Object redactValue(Object value, String path, int depth) {
        if (value instanceof Map) {
            return redactTree((Map<String, Object>) value, path, depth + 1);
        }
        if (value instanceof List) {
            List<Object> items = new ArrayList<>();
            for (Object item : (List<Object>) value) {
                if (items.size() >= MAX_ENTRIES_PER_LIST) {
                    break;
                }
                items.add(redactValue(item, path, depth));
            }
            return items;
        }
        if (value == null || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        return replaceValuePatterns(redactor.redact(value.toString()));
    }

    /** A {@code *} segment in an excluded path stands for any key, so a secret inside the values of a map is found. */
    private boolean isExcluded(String path) {
        if (excludedPaths.contains(path)) {
            return true;
        }
        if (wildcardPaths.isEmpty()) {
            return false;
        }
        String[] actual = segments(path);
        for (String[] pattern : wildcardPaths) {
            if (matches(pattern, actual)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(String[] pattern, String[] segments) {
        if (pattern.length != segments.length) {
            return false;
        }
        for (int index = 0; index < pattern.length; index++) {
            if (!"*".equals(pattern[index]) && !pattern[index].equals(segments[index])) {
                return false;
            }
        }
        return true;
    }

    private String replaceValuePatterns(String input) {
        String text = input;
        for (final Pattern pattern : valuePatterns) {
            final String current = text;
            // A pattern that fails on this input hides the whole value: a report must never leak what it could not check.
            text = Failures.runCatchingNonFatal(() -> pattern.matcher(current).replaceAll(SECRET_VALUE))
                .getOrDefault(SECRET_VALUE);
        }
        return text;
    }

    private static boolean isBuiltInSecret(String key, String path) {
        return BUILT_IN_SECRET_KEY.matcher(key).matches() || BUILT_IN_SECRET_KEY.matcher(path).matches();
    }

    private boolean matchesKeyPattern(final String key, final String path) {
        for (final Pattern pattern : keyPatterns) {
            // A pattern that fails on this input counts as a match: hiding too much is the safe error.
            boolean matched = Failures.runCatchingNonFatal(
                () -> pattern.matcher(key).find() || pattern.matcher(path).find()).getOrDefault(Boolean.TRUE);
            if (matched) {
                return true;
            }
        }
        return false;
    }

    private static String[] segments(String path) {
        return path.split("\\.", -1);
    }

    private static boolean contains(String[] values, String wanted) {
        for (String value : values) {
            if (wanted.equals(value)) {
                return true;
            }
        }
        return false;
    }
}
