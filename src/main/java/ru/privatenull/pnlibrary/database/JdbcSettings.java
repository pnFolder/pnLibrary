package ru.privatenull.pnlibrary.database;

import java.nio.file.Path;

public record JdbcSettings(
        DatabaseType type,
        String jdbcUrl,
        String username,
        String password,
        int maximumPoolSize,
        long connectionTimeoutMillis
) implements DatabaseSettings {
    public JdbcSettings {
        if (type == null || !type.isJdbc()) throw new IllegalArgumentException("JDBC type must be SQLITE or MYSQL");
        if (jdbcUrl == null || jdbcUrl.isBlank()) throw new IllegalArgumentException("JDBC URL cannot be blank");
        username = username == null ? "" : username;
        password = password == null ? "" : password;
        maximumPoolSize = type == DatabaseType.SQLITE ? 1 : Math.max(1, maximumPoolSize);
        connectionTimeoutMillis = Math.max(250L, connectionTimeoutMillis);
    }

    public static JdbcSettings sqlite(Path file, long connectionTimeoutMillis) {
        if (file == null) throw new IllegalArgumentException("SQLite file cannot be null");
        return new JdbcSettings(DatabaseType.SQLITE, "jdbc:sqlite:" + file.toAbsolutePath().normalize(),
                "", "", 1, connectionTimeoutMillis);
    }

    public static JdbcSettings mysql(String host, int port, String database, String username, String password,
                                     String parameters, int maximumPoolSize, long connectionTimeoutMillis) {
        if (host == null || host.isBlank()) throw new IllegalArgumentException("MySQL host cannot be blank");
        if (database == null || database.isBlank()) throw new IllegalArgumentException("MySQL database cannot be blank");
        if (port < 1 || port > 65_535) throw new IllegalArgumentException("Invalid MySQL port: " + port);
        String suffix = parameters == null || parameters.isBlank() ? "" : "?" + parameters;
        return new JdbcSettings(DatabaseType.MYSQL,
                "jdbc:mysql://" + host.trim() + ":" + port + "/" + database.trim() + suffix,
                username, password, maximumPoolSize, connectionTimeoutMillis);
    }

    public static JdbcSettings mysqlUrl(String jdbcUrl, String username, String password,
                                        int maximumPoolSize, long connectionTimeoutMillis) {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:mysql://")) {
            throw new IllegalArgumentException("MySQL JDBC URL must start with jdbc:mysql://");
        }
        return new JdbcSettings(DatabaseType.MYSQL, jdbcUrl.trim(), username, password,
                maximumPoolSize, connectionTimeoutMillis);
    }
}
