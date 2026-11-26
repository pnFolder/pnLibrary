package org.pnfolder.pnlibrary.updates.databases.mysql;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.updates.databases.DatabaseConnection;
import org.pnfolder.pnlibrary.updates.databases.DatabaseEngine;
import org.pnfolder.pnlibrary.updates.databases.JdbcConnections;
import org.pnfolder.pnlibrary.updates.databases.SqlScriptRestore;

/** MySQL and MariaDB through JDBC. */
public final class MysqlEngine implements DatabaseEngine {
    private final JdbcConnections connections;

    public MysqlEngine() {
        this(JdbcConnections.mysql());
    }

    public MysqlEngine(@NotNull JdbcConnections connections) {
        this.connections = connections;
    }

    @Override
    public void dump(@NotNull DatabaseConnection connection, @NotNull List<String> tables, @NotNull Path target) throws Exception {
        try (Connection opened = connections.open(connection)) {
            new MysqlDump().write(opened, tables, target);
        }
    }

    @Override
    public void restore(@NotNull DatabaseConnection connection, @NotNull Path source) throws Exception {
        SqlScriptRestore restore = new SqlScriptRestore(
                MysqlDump.HEADER,
                Arrays.asList("SET SESSION sql_mode = REPLACE(@@sql_mode, 'NO_BACKSLASH_ESCAPES', '')",
                        "SET SESSION FOREIGN_KEY_CHECKS = 0"),
                Collections.singletonList("SET SESSION FOREIGN_KEY_CHECKS = 1"));
        try (Connection opened = connections.open(connection)) {
            restore.read(opened, source);
        }
    }
}
