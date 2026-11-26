package org.pnfolder.pnlibrary.updates.databases;

import java.util.Objects;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * Writes result sets as multi-row {@code INSERT} statements, 200 rows each, and fails once the copy at
 * {@code target} grows past {@code maximumBytes}. Each dialect supplies how one column becomes an SQL literal.
 */
public final class SqlRowWriter {
    /** Rows the driver fetches at once while a table is read. */
    public static final int FETCH_ROWS = 1000;

    /** Column types written as binary literals. */
    public static final Set<Integer> BINARY_TYPES = Collections.unmodifiableSet(new HashSet<>(
            Arrays.asList(Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB)));

    private static final int ROWS_PER_INSERT = 200;

    private final BufferedWriter out;
    private final Path target;
    private final long maximumBytes;
    private final RowLiteral literal;

    public SqlRowWriter(@NotNull BufferedWriter out, @NotNull Path target, long maximumBytes, @NotNull RowLiteral literal) {
        this.out = Objects.requireNonNull(out, "out");
        this.target = Objects.requireNonNull(target, "target");
        this.maximumBytes = maximumBytes;
        this.literal = Objects.requireNonNull(literal, "literal");
    }

    /** Writes every row of {@code rows}; {@code insertHead} is the statement up to and including the opening parenthesis. */
    public void write(@NotNull ResultSet rows, @NotNull String insertHead) throws IOException, SQLException {
        int columns = rows.getMetaData().getColumnCount();
        int inBatch = 0;
        while (rows.next()) {
            out.write(inBatch == 0 ? insertHead : ",(");
            for (int column = 1; column <= columns; column++) {
                if (column > 1) {
                    out.write(",");
                }
                out.write(literal.literal(rows, column));
            }
            out.write(")");
            if (++inBatch == ROWS_PER_INSERT) {
                out.write(";\n");
                inBatch = 0;
                out.flush();
                if (Files.size(target) > maximumBytes) {
                    throw new IllegalStateException("Копия базы больше " + maximumBytes / (1024 * 1024) + " МБ");
                }
            }
        }
        if (inBatch > 0) {
            out.write(";\n");
        }
    }
}
