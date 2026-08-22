package ru.privatenull.pnlibrary.database;

public record RedisSettings(String uri, String namespace) implements DatabaseSettings {
    public RedisSettings {
        if (uri == null || uri.isBlank()) throw new IllegalArgumentException("Redis URI cannot be blank");
        if (namespace == null || namespace.isBlank()) throw new IllegalArgumentException("Redis namespace cannot be blank");
        uri = uri.trim();
        namespace = namespace.trim().toLowerCase().replaceAll("[^a-z0-9:_-]", "_");
    }

    @Override
    public DatabaseType type() {
        return DatabaseType.REDIS;
    }
}
