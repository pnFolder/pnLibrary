package ru.privatenull.pnlibrary.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Owns a JDBC pool and applies ordered, versioned schema migrations. */
public final class JdbcDatabase implements AutoCloseable {
    private final JdbcSettings settings;
    private HikariDataSource dataSource;

    public JdbcDatabase(JdbcSettings settings) {
        this.settings = settings;
    }

    public synchronized void open() {
        if (dataSource != null && !dataSource.isClosed()) return;
        HikariConfig config = new HikariConfig();
        config.setPoolName("pnLibrary-" + settings.type().name().toLowerCase());
        config.setJdbcUrl(settings.jdbcUrl());
        config.setMaximumPoolSize(settings.maximumPoolSize());
        config.setMinimumIdle(1);
        config.setConnectionTimeout(settings.connectionTimeoutMillis());
        config.setDriverClassName(settings.type() == DatabaseType.SQLITE
                ? "org.sqlite.JDBC" : "com.mysql.cj.jdbc.Driver");
        if (settings.type() == DatabaseType.MYSQL) {
            config.setUsername(settings.username());
            config.setPassword(settings.password());
        }
        dataSource = new HikariDataSource(config);
    }

    public Connection connection() throws SQLException {
        HikariDataSource current = dataSource;
        if (current == null || current.isClosed()) throw new IllegalStateException("JDBC database is not open");
        return current.getConnection();
    }

    public DatabaseType type() {
        return settings.type();
    }

    public void migrate(String namespace, List<JdbcMigration> migrations) throws Exception {
        String safeNamespace = namespace == null ? "" : namespace.toLowerCase().replaceAll("[^a-z0-9_]", "");
        if (safeNamespace.isBlank()) throw new IllegalArgumentException("Migration namespace is invalid");
        String historyTable = safeNamespace + "_schema_history";
        List<JdbcMigration> ordered = migrations.stream()
                .sorted(Comparator.comparingInt(JdbcMigration::version))
                .toList();
        Set<Integer> versions = new HashSet<>();
        for (JdbcMigration migration : ordered) {
            if (!versions.add(migration.version())) {
                throw new IllegalArgumentException("Duplicate migration version: " + migration.version());
            }
        }

        try (Connection connection = connection()) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + historyTable
                        + " (version INTEGER PRIMARY KEY, name VARCHAR(191) NOT NULL, installed_at BIGINT NOT NULL)");
            }
            Set<Integer> applied = appliedVersions(connection, historyTable);
            for (JdbcMigration migration : ordered) {
                if (!applied.contains(migration.version())) apply(connection, historyTable, migration);
            }
        }
    }

    private Set<Integer> appliedVersions(Connection connection, String historyTable) throws Exception {
        Set<Integer> result = new HashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT version FROM " + historyTable)) {
            while (rows.next()) result.add(rows.getInt(1));
        }
        return result;
    }

    private void apply(Connection connection, String historyTable, JdbcMigration migration) throws Exception {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            try (Statement statement = connection.createStatement()) {
                for (String sql : migration.statements(settings.type())) statement.executeUpdate(sql);
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO " + historyTable + " (version,name,installed_at) VALUES (?,?,?)")) {
                statement.setInt(1, migration.version());
                statement.setString(2, migration.name());
                statement.setLong(3, System.currentTimeMillis());
                statement.executeUpdate();
            }
            connection.commit();
        } catch (Exception exception) {
            try {
                connection.rollback();
            } catch (Exception rollbackFailure) {
                exception.addSuppressed(rollbackFailure);
            }
            throw exception;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    @Override
    public synchronized void close() {
        if (dataSource != null) dataSource.close();
        dataSource = null;
    }
}
