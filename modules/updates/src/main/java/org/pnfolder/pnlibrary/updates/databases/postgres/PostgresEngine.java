package org.pnfolder.pnlibrary.updates.databases.postgres;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.updates.databases.DatabaseConnection;
import org.pnfolder.pnlibrary.updates.databases.DatabaseEngine;
import org.pnfolder.pnlibrary.updates.databases.JdbcConnections;
import org.pnfolder.pnlibrary.updates.databases.SqlScriptRestore;

/** PostgreSQL through JDBC. */
public final class PostgresEngine implements DatabaseEngine {
    private final JdbcConnections connections;

    public PostgresEngine() {
        this(JdbcConnections.postgres());
    }

    public PostgresEngine(@NotNull JdbcConnections connections) {
        this.connections = connections;
    }

    @Override
    public void dump(@NotNull DatabaseConnection connection, @NotNull List<String> tables, @NotNull Path target) throws Exception {
        try (Connection opened = connections.open(connection)) {
            new PostgresDump().write(opened, tables, target);
        }
    }

    @Override
    public void restore(@NotNull DatabaseConnection connection, @NotNull Path source) throws Exception {
        SqlScriptRestore restore = SqlScriptRestore.atomic(PostgresDump.HEADER);
        try (Connection opened = connections.open(connection)) {
            restore.read(opened, source);
        }
    }
}
