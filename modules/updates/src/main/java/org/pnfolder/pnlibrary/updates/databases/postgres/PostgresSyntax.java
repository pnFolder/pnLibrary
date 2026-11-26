package org.pnfolder.pnlibrary.updates.databases.postgres;

import java.util.Locale;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/** Quoting for the statements a copy of a PostgreSQL database is made of. */
public final class PostgresSyntax {
    private PostgresSyntax() {
    }

    /** {@code name} quoted as a PostgreSQL identifier. */
    @NotNull
    public static String identifier(@NotNull String name) {
        return "\"" + name.replace("\"", "\"\"") + "\"";
    }

    /**
     * A text literal on one line. The E prefix lets line breaks and backslashes be written as escapes, and a quote is
     * doubled; PostgreSQL turns the text into the column's type, so one form fits every column.
     */
    @NotNull
    public static String literal(@NotNull String text) {
        StringBuilder out = new StringBuilder(text.length() + 3).append("E'");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\':
                    out.append("\\\\");
                    break;
                case '\'':
                    out.append("''");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                default:
                    out.append(c);
            }
        }
        return out.append('\'').toString();
    }

    /** Binary {@code value} as a hex literal. */
    @NotNull
    public static String bytes(@NotNull byte[] value) {
        StringBuilder hex = new StringBuilder("'\\x");
        for (byte b : value) {
            hex.append(String.format(Locale.ROOT, "%02x", b));
        }
        return hex.append('\'').toString();
    }

    /** Creates an enum type once; a type that is already there is left alone. */
    @NotNull
    public static String createEnum(@NotNull String name, @NotNull List<String> labels) {
        StringBuilder list = new StringBuilder();
        boolean dollars = false;
        for (String label : labels) {
            if (list.length() > 0) {
                list.append(", ");
            }
            list.append(literal(label));
            dollars |= label.contains("$$");
        }
        String body = "CREATE TYPE " + identifier(name) + " AS ENUM (" + list + ")";
        String tag = dollars ? "$pn$" : "$$";
        return "DO " + tag + " BEGIN " + body + "; EXCEPTION WHEN duplicate_object THEN NULL; END " + tag;
    }

    /** A sequence with the settings it has now, so numbering continues the way the plugin set it up. */
    @NotNull
    public static String createSequence(@NotNull String name, @NotNull String type, long start, long increment, long min,
                                        long max, boolean cycle) {
        return "CREATE SEQUENCE IF NOT EXISTS " + identifier(name) + " AS " + type + " INCREMENT BY " + increment
                + " MINVALUE " + min + " MAXVALUE " + max + " START WITH " + start + " " + (cycle ? "" : "NO ") + "CYCLE";
    }
}
