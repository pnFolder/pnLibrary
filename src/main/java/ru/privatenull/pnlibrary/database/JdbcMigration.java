package ru.privatenull.pnlibrary.database;

import java.util.List;

public record JdbcMigration(
        int version,
        String name,
        List<String> sqliteStatements,
        List<String> mysqlStatements
) {
    public JdbcMigration {
        if (version < 1) throw new IllegalArgumentException("Migration version must be positive");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Migration name cannot be blank");
        sqliteStatements = List.copyOf(sqliteStatements == null ? List.of() : sqliteStatements);
        mysqlStatements = List.copyOf(mysqlStatements == null ? List.of() : mysqlStatements);
        if (sqliteStatements.isEmpty() || mysqlStatements.isEmpty()) {
            throw new IllegalArgumentException("Migration must provide both SQLite and MySQL statements");
        }
    }

    public List<String> statements(DatabaseType type) {
        return type == DatabaseType.SQLITE ? sqliteStatements : mysqlStatements;
    }
}
