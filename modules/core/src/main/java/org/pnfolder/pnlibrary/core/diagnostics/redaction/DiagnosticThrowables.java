package org.pnfolder.pnlibrary.core.diagnostics.redaction;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Shared rules for grouping repeated failures: the significant cause, its origin frame, and a stable fingerprint. */
public final class DiagnosticThrowables {
    /** Matches a canonical UUID in any letter case. */
    public static final Pattern UUID_PATTERN =
        Pattern.compile("(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b");

    private static final int FINGERPRINT_BYTES = 8;
    private static final String HEX_DIGITS = "0123456789abcdef";
    private static final Pattern LONG_NUMBER_PATTERN = Pattern.compile("\\b\\d{4,}\\b");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final List<String> IGNORED_FRAME_PREFIXES = Arrays.asList(
        "java.", "javax.", "kotlin.", "kotlinx.", "sun.", "jdk.",
        "org.bukkit.", "net.minecraft.", "io.papermc.", "com.destroystokyo.paper.",
        "org.pnfolder.pnlibrary.");

    private DiagnosticThrowables() {
    }

    /** Strips asynchronous wrappers so the failure that actually happened is reported. */
    @Nullable
    public static Throwable significantCause(@Nullable Throwable error) {
        if (error == null) {
            return null;
        }
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
            && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    /** Returns the first frame outside the JDK, the server and pnLibrary, or the top frame when there is none. */
    @Nullable
    public static StackTraceElement originFrame(@Nullable Throwable error) {
        Throwable significant = significantCause(error);
        if (significant == null) {
            return null;
        }
        StackTraceElement[] trace = significant.getStackTrace();
        for (StackTraceElement frame : trace) {
            if (isApplicationFrame(frame)) {
                return frame;
            }
        }
        return trace.length == 0 ? null : trace[0];
    }

    /** Describes {@link #originFrame(Throwable)} of {@code error} as report data, or {@code null} for an empty trace. */
    @Nullable
    public static Map<String, Object> originData(@NotNull Throwable error) {
        StackTraceElement frame = originFrame(error);
        if (frame == null) {
            return null;
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("class", frame.getClassName());
        data.put("method", frame.getMethodName());
        data.put("file", frame.getFileName());
        data.put("line", frame.getLineNumber() >= 0 ? Integer.valueOf(frame.getLineNumber()) : null);
        return data;
    }

    /**
     * Fingerprints this failure (or none) from its {@code context}, message and significant cause.
     *
     * <p>Identifiers and long numbers are masked, so repeats of one problem share an id.
     */
    @NotNull
    public static String fingerprint(@Nullable Throwable error, @NotNull List<String> context, @NotNull String message) {
        Throwable significant = significantCause(error);
        StackTraceElement frame = originFrame(error);
        List<String> parts = new ArrayList<String>(context);
        parts.add(normalize(message));
        parts.add(significant == null ? "" : significant.getClass().getName());
        parts.add(normalize(significant == null || significant.getMessage() == null ? "" : significant.getMessage()));
        parts.add(frame == null ? "" : frame.getClassName());
        parts.add(frame == null ? "" : frame.getMethodName());
        return hex(sha256(join(parts).getBytes(StandardCharsets.UTF_8)));
    }

    private static String join(List<String> parts) {
        StringBuilder source = new StringBuilder();
        for (int index = 0; index < parts.size(); index++) {
            if (index > 0) {
                source.append('\u0000');
            }
            source.append(parts.get(index));
        }
        return source.toString();
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException("SHA-256 is required by every Java runtime", missing);
        }
    }

    private static String hex(byte[] digest) {
        char[] hex = new char[FINGERPRINT_BYTES * 2];
        for (int index = 0; index < FINGERPRINT_BYTES; index++) {
            int value = digest[index] & 0xFF;
            hex[index * 2] = HEX_DIGITS.charAt(value >>> 4);
            hex[index * 2 + 1] = HEX_DIGITS.charAt(value & 0x0F);
        }
        return new String(hex);
    }

    private static boolean isApplicationFrame(StackTraceElement frame) {
        for (String prefix : IGNORED_FRAME_PREFIXES) {
            if (frame.getClassName().startsWith(prefix)) {
                return false;
            }
        }
        return true;
    }

    private static String normalize(String value) {
        String text = UUID_PATTERN.matcher(value).replaceAll("<uuid>");
        text = LONG_NUMBER_PATTERN.matcher(text).replaceAll("<number>");
        text = WHITESPACE_PATTERN.matcher(text).replaceAll(" ");
        return Whitespace.trim(text);
    }
}
