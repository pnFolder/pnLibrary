package org.pnfolder.pnlibrary.common.text;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Case-insensitive wildcard matching used by access policies: {@code *} matches any character sequence, including an
 * empty one, and every other character is matched literally.
 */
public final class Wildcards {
    private static final int MAXIMUM_CACHED = 1024;

    private static final ConcurrentHashMap<String, Pattern> COMPILED = new ConcurrentHashMap<>();
    private static final AtomicInteger COMPILATIONS = new AtomicInteger();

    private Wildcards() {
    }

    /** How many patterns were compiled since start; used by tests to prove the cache works. */
    static int compilationCount() {
        return COMPILATIONS.get();
    }

    /** Returns whether the whole of {@code value} matches {@code pattern}, ignoring case. */
    public static boolean matches(@NotNull String pattern, @NotNull String value) {
        return patternFor(pattern).matcher(value).matches();
    }

    // Patterns come from configuration, so the set is small; the cap only guards against a caller feeding unbounded text.
    private static Pattern patternFor(String pattern) {
        Pattern cached = COMPILED.get(pattern);
        if (cached != null) {
            return cached;
        }
        if (COMPILED.size() >= MAXIMUM_CACHED) {
            COMPILED.clear();
        }
        return COMPILED.computeIfAbsent(pattern, Wildcards::compile);
    }

    private static Pattern compile(String pattern) {
        COMPILATIONS.incrementAndGet();
        // A negative limit keeps trailing empty parts, so "shop-*" and "*" split the same way Kotlin's split does.
        String expression = Arrays.stream(pattern.split("\\*", -1))
            .map(Pattern::quote)
            .collect(Collectors.joining(".*"));
        return Pattern.compile("^" + expression + "$", Pattern.CASE_INSENSITIVE);
    }
}
