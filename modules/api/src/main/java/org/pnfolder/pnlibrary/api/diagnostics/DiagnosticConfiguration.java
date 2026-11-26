package org.pnfolder.pnlibrary.api.diagnostics;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Per-file collection and redaction contract declared by a contributing plugin.
 *
 * <p>Use {@link #builder} to obtain a {@link Builder}.
 */
public final class DiagnosticConfiguration {
    private static final Pattern VALID_PATH = Pattern.compile("[A-Za-z0-9_./-]+\\.(?:yml|yaml|json|properties|toml|conf)");

    private final String path;
    private final List<String> excludedPaths;
    private final List<String> secretKeyPatterns;
    private final List<String> valuePatterns;

    private DiagnosticConfiguration(Builder builder) {
        this.path = validatePath(builder.path);
        this.excludedPaths = immutable(builder.excludedPaths, 64, 256);
        this.secretKeyPatterns = immutable(builder.secretKeyPatterns, 32, 256);
        this.valuePatterns = immutable(builder.valuePatterns, 32, 256);
    }

    /** Canonical entry point for the fluent builder. */
    @NotNull
    public static Builder builder(@NotNull String path) {
        return new Builder(path);
    }

    /** Relative path inside the plugin data folder. Validated at construction time. */
    @NotNull
    public String getPath() {
        return path;
    }

    /** Dotted key-paths whose entire subtrees are excluded from the report. */
    @NotNull
    public List<String> getExcludedPaths() {
        return excludedPaths;
    }

    /** Regex patterns: values whose key or full dotted-path matches are redacted. */
    @NotNull
    public List<String> getSecretKeyPatterns() {
        return secretKeyPatterns;
    }

    /** Regex patterns: matching fragments inside string values are replaced. */
    @NotNull
    public List<String> getValuePatterns() {
        return valuePatterns;
    }

    private static String validatePath(String value) {
        if (value == null || value.startsWith("/") || value.contains("..") || !VALID_PATH.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid diagnostic configuration path: " + value);
        }
        return value;
    }

    private static List<String> immutable(List<String> source, int maxItems, int maxLength) {
        List<String> result = new ArrayList<>();
        for (String item : source) {
            if (item == null || item.trim().isEmpty() || item.length() > maxLength || result.size() >= maxItems) {
                throw new IllegalArgumentException("Invalid diagnostic redaction rule: " + item);
            }
            result.add(item.trim());
        }
        return Collections.unmodifiableList(result);
    }

    /** Fluent builder for one configuration-file collection policy. */
    public static final class Builder {
        private final String path;
        private final List<String> excludedPaths = new ArrayList<>();
        private final List<String> secretKeyPatterns = new ArrayList<>();
        private final List<String> valuePatterns = new ArrayList<>();

        private Builder(String path) {
            this.path = path;
        }

        /** Omits this exact dotted path and all of its children from the report. */
        @NotNull
        public Builder exclude(@NotNull String dottedPath) {
            excludedPaths.add(dottedPath);
            return this;
        }

        /** Redacts values whose key or full dotted path matches the given Java regex. */
        @NotNull
        public Builder secretKeyRegex(@NotNull String expression) {
            secretKeyPatterns.add(expression);
            return this;
        }

        /** Replaces regex-matched fragments in string values with {@code [REDACTED BY PLUGIN]}. */
        @NotNull
        public Builder redactValueRegex(@NotNull String expression) {
            valuePatterns.add(expression);
            return this;
        }

        /** Validates the path and rules and creates an immutable configuration. */
        @NotNull
        public DiagnosticConfiguration build() {
            return new DiagnosticConfiguration(this);
        }
    }
}
