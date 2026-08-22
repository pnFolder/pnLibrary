package ru.privatenull.pnlibrary.database;

import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.nio.file.Path;

/** Reads the shared database configuration without adding a pnLibrary config namespace. */
public sealed interface DatabaseSettings permits JdbcSettings, MongoSettings, RedisSettings {
    DatabaseType type();

    static DatabaseSettings from(ConfigurationSection section, File dataFolder) {
        if (section == null) throw new IllegalArgumentException("database section is not configured");
        DatabaseType type = DatabaseType.parse(section.getString("type", "SQLITE"));
        return switch (type) {
            case SQLITE -> sqlite(section, dataFolder);
            case MYSQL -> mysql(section);
            case MONGODB -> mongo(section);
            case REDIS -> redis(section);
        };
    }

    private static JdbcSettings sqlite(ConfigurationSection root, File dataFolder) {
        String fileName = root.getString("sqlite.file", "database.db");
        if (fileName == null || fileName.isBlank()) throw new IllegalArgumentException("database.sqlite.file is blank");
        Path rootPath = dataFolder.toPath().toAbsolutePath().normalize();
        Path databaseFile = rootPath.resolve(fileName).normalize();
        if (!databaseFile.startsWith(rootPath)) {
            throw new IllegalArgumentException("database.sqlite.file must stay inside the plugin data folder");
        }
        return JdbcSettings.sqlite(databaseFile, root.getLong("sqlite.connection-timeout-ms", 10_000L));
    }

    private static JdbcSettings mysql(ConfigurationSection root) {
        ConfigurationSection mysql = root.getConfigurationSection("mysql");
        if (mysql == null) throw new IllegalArgumentException("database.mysql is not configured");
        String directUrl = mysql.getString("url", "");
        if (directUrl != null && !directUrl.isBlank()) {
            return JdbcSettings.mysqlUrl(directUrl, mysql.getString("username", "root"),
                    mysql.getString("password", ""), mysql.getInt("pool-size", 10),
                    mysql.getLong("connection-timeout-ms", 10_000L));
        }
        return JdbcSettings.mysql(
                required(mysql, "host"),
                mysql.getInt("port", 3306),
                required(mysql, "database"),
                mysql.getString("username", "root"),
                mysql.getString("password", ""),
                mysql.getString("parameters", "useSSL=false&serverTimezone=UTC"),
                mysql.getInt("pool-size", 10),
                mysql.getLong("connection-timeout-ms", 10_000L)
        );
    }

    private static MongoSettings mongo(ConfigurationSection root) {
        ConfigurationSection mongo = root.getConfigurationSection("mongodb");
        if (mongo == null) mongo = root.getConfigurationSection("mongo");
        if (mongo == null) throw new IllegalArgumentException("database.mongodb is not configured");
        return new MongoSettings(
                required(mongo, "uri"),
                required(mongo, "database"),
                mongo.getString("collection", "data")
        );
    }

    private static RedisSettings redis(ConfigurationSection root) {
        ConfigurationSection redis = root.getConfigurationSection("redis");
        if (redis == null) throw new IllegalArgumentException("database.redis is not configured");
        return new RedisSettings(required(redis, "uri"), redis.getString("namespace", "privatenull"));
    }

    private static String required(ConfigurationSection section, String path) {
        String value = section.getString(path);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(section.getCurrentPath() + "." + path + " is blank");
        return value.trim();
    }
}
