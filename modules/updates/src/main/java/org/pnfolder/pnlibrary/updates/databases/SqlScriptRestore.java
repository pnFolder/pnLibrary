package org.pnfolder.pnlibrary.updates.databases;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;
import java.util.zip.GZIPInputStream;
import org.jetbrains.annotations.NotNull;

/**
 * Puts a copy of a SQL database back by running its statements, one per line: the tables the file names are
 * dropped and created again with their rows. Tables the file does not mention stay as they are.
 */
public final class SqlScriptRestore {
    private final String header;
    private final List<String> setup;
    private final List<String> teardown;
    private final boolean atomic;

    /** A restore that runs the file statement by statement with autocommit. */
    public SqlScriptRestore(@NotNull String header) {
        this(header, Collections.<String>emptyList(), Collections.<String>emptyList(), false);
    }

    /**
     * A restore with {@code setup} statements run before the file, such as relaxing foreign key checks, and
     * {@code teardown} statements run after it, even when it fails.
     */
    public SqlScriptRestore(@NotNull String header, @NotNull List<String> setup, @NotNull List<String> teardown) {
        this(header, setup, teardown, false);
    }

    private SqlScriptRestore(String header, List<String> setup, List<String> teardown, boolean atomic) {
        this.header = header;
        this.setup = setup;
        this.teardown = teardown;
        this.atomic = atomic;
    }

    /**
     * A restore that runs the whole file in one transaction rolled back on failure; only for databases with
     * transactional DDL (PostgreSQL), since MySQL commits implicitly at every {@code DROP TABLE}.
     */
    public static SqlScriptRestore atomic(@NotNull String header) {
        return new SqlScriptRestore(header, Collections.<String>emptyList(), Collections.<String>emptyList(), true);
    }

    /** Runs the statements of {@code source} on {@code connection}; the teardown runs even when a statement fails. */
    public void read(@NotNull Connection connection, @NotNull Path source) throws IOException, SQLException {
        connection.setAutoCommit(!atomic);
        try (Statement statement = connection.createStatement()) {
            for (String sql : setup) {
                statement.execute(sql);
            }
            try {
                run(connection, statement, source);
            } finally {
                for (String sql : teardown) {
                    statement.execute(sql);
                }
            }
        }
    }

    private void run(Connection connection, Statement statement, Path source) throws IOException, SQLException {
        try {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new GZIPInputStream(Files.newInputStream(source)), StandardCharsets.UTF_8))) {
                if (!header.equals(reader.readLine())) {
                    throw new IllegalArgumentException("Это не копия базы pnLibrary для этого типа базы");
                }
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!Blank.is(line)) {
                        statement.execute(line);
                    }
                }
            }
            if (atomic) {
                connection.commit();
            }
        } catch (Throwable error) {
            if (atomic) {
                rollback(connection, error);
            }
            throw error;
        }
    }

    private static void rollback(Connection connection, Throwable error) {
        try {
            connection.rollback();
        } catch (Throwable failure) {
            error.addSuppressed(failure);
        }
    }
}
