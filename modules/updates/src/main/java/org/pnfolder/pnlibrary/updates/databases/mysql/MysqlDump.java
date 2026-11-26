package org.pnfolder.pnlibrary.updates.databases.mysql;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.updates.databases.DumpBody;
import org.pnfolder.pnlibrary.updates.databases.DumpFiles;
import org.pnfolder.pnlibrary.updates.databases.RowLiteral;
import org.pnfolder.pnlibrary.updates.databases.SqlRowWriter;
import org.pnfolder.pnlibrary.updates.databases.TableMask;

/**
 * Writes every table of a database into one compressed text file of SQL statements, one statement per line: the
 * table definition, then its rows. All tables are read in one consistent transaction, so rows match each other.
 */
public final class MysqlDump {
    /** First line of every copy; the restore refuses a file without it. */
    public static final String HEADER = "-- pnlibrary-db 1";

    private static final Set<Integer> NUMBER_TYPES = Collections.unmodifiableSet(new HashSet<Integer>(Arrays.asList(
            Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT, Types.FLOAT, Types.REAL, Types.DOUBLE,
            Types.DECIMAL, Types.NUMERIC)));

    private final long maximumBytes;

    public MysqlDump() {
        this(2L * 1024 * 1024 * 1024);
    }

    public MysqlDump(long maximumBytes) {
        this.maximumBytes = maximumBytes;
    }

    /** Writes the tables matching {@code only} (all when empty) to {@code target}, failing past the maximum size. */
    public void write(@NotNull final Connection connection, @NotNull final List<String> only, @NotNull final Path target)
            throws IOException, SQLException {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("START TRANSACTION WITH CONSISTENT SNAPSHOT");
        }
        try {
            DumpFiles.write(target, HEADER, new DumpBody() {
                @Override
                public void write(BufferedWriter out) throws IOException, SQLException {
                    SqlRowWriter rowWriter = new SqlRowWriter(out, target, maximumBytes, new RowLiteral() {
                        @Override
                        public String literal(ResultSet rows, int column) throws SQLException {
                            return value(rows, column);
                        }
                    });
                    for (String table : tables(connection)) {
                        if (TableMask.matches(only, table)) {
                            dumpTable(connection, table, out, rowWriter);
                        }
                    }
                }
            });
        } finally {
            connection.rollback();
        }
    }

    private static List<String> tables(Connection connection) throws SQLException {
        List<String> tables = new ArrayList<String>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SHOW FULL TABLES WHERE Table_type = 'BASE TABLE'")) {
            while (rows.next()) {
                tables.add(rows.getString(1));
            }
        }
        return tables;
    }

    private static void dumpTable(Connection connection, String table, BufferedWriter out, SqlRowWriter rowWriter)
            throws IOException, SQLException {
        String quoted = MysqlSyntax.identifier(table);
        out.write("DROP TABLE IF EXISTS " + quoted + ";\n");
        out.write(createStatement(connection, quoted) + ";\n");
        try (Statement statement = connection.createStatement()) {
            statement.setFetchSize(SqlRowWriter.FETCH_ROWS);
            try (ResultSet rows = statement.executeQuery("SELECT * FROM " + quoted)) {
                rowWriter.write(rows, "INSERT INTO " + quoted + " VALUES (");
            }
        }
    }

    private static String createStatement(Connection connection, String quoted) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SHOW CREATE TABLE " + quoted)) {
            if (!rows.next()) {
                throw new IllegalStateException("Нет описания таблицы " + quoted);
            }
            return rows.getString(2).replace('\n', ' ');
        }
    }

    private static String value(ResultSet rows, int column) throws SQLException {
        int type = rows.getMetaData().getColumnType(column);
        if (SqlRowWriter.BINARY_TYPES.contains(type)) {
            byte[] bytes = rows.getBytes(column);
            if (bytes == null) {
                return "NULL";
            }
            StringBuilder hex = new StringBuilder("X'");
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.append('\'').toString();
        }
        String text = rows.getString(column);
        if (text == null) {
            return "NULL";
        }
        return NUMBER_TYPES.contains(type) ? text : MysqlSyntax.literal(text);
    }
}
