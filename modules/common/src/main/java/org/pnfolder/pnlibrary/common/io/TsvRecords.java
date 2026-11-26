package org.pnfolder.pnlibrary.common.io;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The one way the library keeps a table of records on disk: a header line with the format name and version, then one
 * line per record with the fields separated by tabs. Tab, line break and backslash inside a field are escaped, a file
 * is replaced atomically, and a file that is missing or damaged reads as fewer records instead of an error.
 */
public final class TsvRecords {
    private static final String TAB = "\t";
    private static final char BACKSLASH = '\\';

    private TsvRecords() {
    }

    /** The records of {@code path} without its header line; empty when the file is missing or cannot be read. */
    @NotNull
    public static List<List<String>> read(@NotNull Path path) {
        if (!Files.isRegularFile(path)) {
            return Collections.emptyList();
        }
        try {
            return parse(Files.readAllLines(path, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException damaged) {
            return Collections.emptyList();
        }
    }

    /**
     * Replaces {@code path} with {@code header} and {@code records}; a {@code null} field is written empty.
     *
     * @return whether the file was written
     */
    public static boolean write(@NotNull Path path, @NotNull String header, @NotNull List<? extends List<?>> records) {
        List<String> lines = new ArrayList<>();
        lines.add(header);
        for (List<?> fields : records) {
            lines.add(encodeFields(fields));
        }
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            AtomicFiles.write(path, (String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException | RuntimeException failed) {
            return false;
        }
    }

    /** {@code value} with backslash, tab and line break written as {@code \\}, {@code \t} and {@code \n}; carriage returns are dropped. */
    @NotNull
    public static String escape(@NotNull String value) {
        StringBuilder out = new StringBuilder();
        for (char c : value.toCharArray()) {
            if (c == BACKSLASH) {
                out.append(BACKSLASH).append(BACKSLASH);
            } else if (c == '\t') {
                out.append(BACKSLASH).append('t');
            } else if (c == '\n') {
                out.append(BACKSLASH).append('n');
            } else if (c != '\r') {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** The reverse of {@link #escape(String)}. */
    @NotNull
    public static String unescape(@NotNull String value) {
        if (value.indexOf(BACKSLASH) < 0) {
            return value;
        }
        StringBuilder out = new StringBuilder();
        int index = 0;
        while (index < value.length()) {
            char c = value.charAt(index);
            if (c == BACKSLASH && index + 1 < value.length()) {
                index++;
                out.append(unescapedChar(value.charAt(index)));
            } else {
                out.append(c);
            }
            index++;
        }
        return out.toString();
    }

    private static List<List<String>> parse(List<String> lines) {
        List<List<String>> records = new ArrayList<>();
        for (int index = 1; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.isEmpty()) {
                continue;
            }
            List<String> fields = new ArrayList<>();
            for (String field : line.split(TAB, -1)) {
                fields.add(unescape(field));
            }
            records.add(fields);
        }
        return records;
    }

    private static String encodeFields(List<?> fields) {
        List<String> escaped = new ArrayList<>();
        for (Object field : fields) {
            escaped.add(escape(field == null ? "" : field.toString()));
        }
        return String.join(TAB, escaped);
    }

    private static char unescapedChar(char c) {
        if (c == 't') {
            return '\t';
        }
        if (c == 'n') {
            return '\n';
        }
        return c;
    }
}
