package org.pnfolder.pnlibrary.core.observability.report;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Turns plugin names and configuration paths into safe, unique entry names of a support archive. */
public final class ArchivePaths {
    private static final Pattern UNSAFE_NAME = Pattern.compile("[^a-z0-9._-]+");
    private static final Pattern UNSAFE_CONFIG_PART = Pattern.compile("[^A-Za-z0-9._-]+");

    private ArchivePaths() {
    }

    /** {@code pnclans} stays {@code pnclans}; a module {@code pnclans/war} becomes {@code pnclans/modules/war}. */
    @NotNull
    public static String folderOf(@NotNull String name) {
        int slash = name.indexOf('/');
        String plugin = slash < 0 ? name : name.substring(0, slash);
        String module = slash < 0 ? "" : name.substring(slash + 1);
        return module.isEmpty() ? safePath(plugin) : safePath(plugin) + "/modules/" + safePath(module);
    }

    /** Lowercase letters, digits, dots, dashes and underscores only, at most 96 characters, never empty. */
    @NotNull
    public static String safePath(@NotNull String value) {
        String cleaned = UNSAFE_NAME.matcher(value.toLowerCase(Locale.ROOT)).replaceAll("-");
        int start = 0;
        int end = cleaned.length();
        while (start < end && cleaned.charAt(start) == '-') {
            start++;
        }
        while (end > start && cleaned.charAt(end - 1) == '-') {
            end--;
        }
        String trimmed = cleaned.substring(start, end);
        if (trimmed.trim().isEmpty()) {
            return "unknown";
        }
        return trimmed.length() > 96 ? trimmed.substring(0, 96) : trimmed;
    }

    /** A configuration path without empty, {@code .} or {@code ..} parts and without unsafe characters. */
    @NotNull
    public static String safeConfigurationPath(@NotNull String value) {
        List<String> parts = new ArrayList<>();
        for (String part : value.replace('\\', '/').split("/")) {
            if (part.trim().isEmpty() || part.equals(".") || part.equals("..")) {
                continue;
            }
            String clean = UNSAFE_CONFIG_PART.matcher(part).replaceAll("_");
            clean = clean.length() > 128 ? clean.substring(0, 128) : clean;
            parts.add(clean.trim().isEmpty() ? "config" : clean);
        }
        return parts.isEmpty() ? "config.txt" : String.join("/", parts);
    }

    /**
     * Two names that differ only in replaced characters get {@code -2}, {@code -3}, ... before the extension so neither
     * overwrites the other.
     */
    @NotNull
    public static String unique(@NotNull String directory, @NotNull String path, @NotNull Set<String> used) {
        int dot = path.lastIndexOf('.');
        if (dot <= path.lastIndexOf('/') + 1) {
            dot = path.length();
        }
        String candidate = path;
        int number = 2;
        while (!used.add((directory + candidate).toLowerCase(Locale.ROOT))) {
            candidate = path.substring(0, dot) + "-" + number++ + path.substring(dot);
        }
        return candidate;
    }
}
