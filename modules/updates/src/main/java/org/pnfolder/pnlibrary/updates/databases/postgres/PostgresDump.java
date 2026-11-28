package org.pnfolder.pnlibrary.updates.databases.postgres;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.updates.databases.dump.DumpBody;
import org.pnfolder.pnlibrary.updates.databases.dump.DumpFiles;
import org.pnfolder.pnlibrary.updates.databases.dump.RowLiteral;
import org.pnfolder.pnlibrary.updates.databases.dump.SqlRowWriter;
import org.pnfolder.pnlibrary.updates.databases.dump.TableMask;

/**
 * Writes the tables of the current schema into one compressed text file of SQL statements, one per line: sequences,
 * then each table with its rows and indexes, then foreign keys and sequence positions. Every table is read in one
 * consistent read-only transaction. Views, functions, triggers and other schemas are not copied.
 */
public final class PostgresDump {
    /** First line of every copy; the restore refuses a file without it. */
    public static final String HEADER = "-- pnlibrary-pg 1";

    private final long maximumBytes;

    public PostgresDump() {
        this(2L * 1024 * 1024 * 1024);
    }

    public PostgresDump(long maximumBytes) {
        this.maximumBytes = maximumBytes;
    }

    /** Writes the tables matching {@code only} (all when empty) to {@code target}, failing past the maximum size. */
    public void write(@NotNull final Connection connection, @NotNull final List<String> only, @NotNull final Path target)
            throws IOException, SQLException {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY");
        }
        try {
            final PostgresSchema schema = new PostgresSchema(connection);
            DumpFiles.write(target, HEADER, new DumpBody() {
                @Override
                public void write(BufferedWriter out) throws IOException, SQLException {
                    SqlRowWriter rowWriter = new SqlRowWriter(out, target, maximumBytes, new RowLiteral() {
                        @Override
                        public String literal(ResultSet rows, int column) throws SQLException {
                            return value(rows, column);
                        }
                    });
                    writeAll(connection, schema, only, out, rowWriter);
                }
            });
        } finally {
            connection.rollback();
        }
    }

    private static void writeAll(Connection connection, PostgresSchema schema, List<String> only, BufferedWriter out,
                                 SqlRowWriter rowWriter) throws IOException, SQLException {
        List<String> tables = new ArrayList<>();
        for (String table : schema.tables()) {
            if (TableMask.matches(only, table)) {
                tables.add(table);
            }
        }
        // Dropping a table also drops the sequences it owns, so all drops come before the sequences are created.
        for (String table : tables) {
            out.write("DROP TABLE IF EXISTS " + PostgresSyntax.identifier(table) + " CASCADE;\n");
        }
        writeStatements(out, schema.enumTypes(tables));
        writeStatements(out, schema.sequences(only));
        for (String table : tables) {
            dumpTable(connection, schema, table, out, rowWriter);
        }
        for (String table : tables) {
            writeStatements(out, schema.foreignKeys(table));
        }
        writeStatements(out, schema.externalForeignKeys(tables));
        writeStatements(out, schema.sequencePositions(only));
    }

    private static void writeStatements(BufferedWriter out, List<String> statements) throws IOException {
        for (String statement : statements) {
            out.write(statement + ";\n");
        }
    }

    private static void dumpTable(Connection connection, PostgresSchema schema, String table, BufferedWriter out,
                                  SqlRowWriter rowWriter) throws IOException, SQLException {
        String quoted = PostgresSyntax.identifier(table);
        out.write(schema.createTable(table) + ";\n");
        StringBuilder list = new StringBuilder();
        for (PostgresSchema.Column column : schema.columns(table)) {
            if (!column.isGenerated()) {
                list.append(list.length() > 0 ? ", " : "").append(PostgresSyntax.identifier(column.getName()));
            }
        }
        if (list.length() > 0) {
            try (Statement statement = connection.createStatement()) {
                statement.setFetchSize(SqlRowWriter.FETCH_ROWS);
                try (ResultSet rows = statement.executeQuery("SELECT " + list + " FROM " + quoted)) {
                    rowWriter.write(rows, "INSERT INTO " + quoted + " (" + list + ") OVERRIDING SYSTEM VALUE VALUES (");
                }
            }
        }
        writeStatements(out, schema.indexes(table));
    }

    private static String value(ResultSet rows, int column) throws SQLException {
        if (SqlRowWriter.BINARY_TYPES.contains(rows.getMetaData().getColumnType(column))) {
            byte[] bytes = rows.getBytes(column);
            return bytes != null ? PostgresSyntax.bytes(bytes) : "NULL";
        }
        String text = rows.getString(column);
        return text != null ? PostgresSyntax.literal(text) : "NULL";
    }
}
