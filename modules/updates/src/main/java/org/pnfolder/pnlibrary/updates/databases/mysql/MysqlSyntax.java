package org.pnfolder.pnlibrary.updates.databases.mysql;

import org.jetbrains.annotations.NotNull;

/** Quoting for the statements a copy of a MySQL database is made of. */
public final class MysqlSyntax {
    private MysqlSyntax() {
    }

    /** {@code name} quoted as a MySQL identifier. */
    @NotNull
    public static String identifier(@NotNull String name) {
        return "`" + name.replace("`", "``") + "`";
    }

    /** A text literal on one line: every character that could end or change the statement is escaped. */
    @NotNull
    public static String literal(@NotNull String text) {
        StringBuilder out = new StringBuilder(text.length() + 2).append('\'');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\':
                    out.append("\\\\");
                    break;
                case '\'':
                    out.append("\\'");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\u0000':
                    out.append("\\0");
                    break;
                case '\u001a':
                    out.append("\\Z");
                    break;
                default:
                    out.append(c);
            }
        }
        return out.append('\'').toString();
    }

    /**
     * {@code sql} on one line. A line break inside a quoted string (a DEFAULT or COMMENT) becomes {@code \n}, which keeps the text;
     * elsewhere it is only white space and becomes a space.
     */
    @NotNull
    public static String singleLine(@NotNull String sql) {
        StringBuilder out = new StringBuilder(sql.length());
        char quote = 0;
        int index = 0;
        while (index < sql.length()) {
            char c = sql.charAt(index++);
            boolean inText = quote == '\'' || quote == '"';
            if (c == '\n' && inText) {
                out.append("\\n");
            } else if (c == '\r' && inText) {
                out.append("\\r");
            } else if (c == '\n' || c == '\r') {
                out.append(' ');
            } else if (inText && c == '\\' && index < sql.length()) {
                out.append(c).append(sql.charAt(index++));
            } else if (quote == 0 && (c == '\'' || c == '"' || c == '`')) {
                quote = c;
                out.append(c);
            } else if (quote != 0 && c == quote) {
                quote = 0;
                out.append(c);
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
