package ru.privatenull.pnlibrary.database;

import java.util.Locale;

public enum DatabaseType {
    SQLITE,
    MYSQL,
    MONGODB,
    REDIS;

    public static DatabaseType parse(String value) {
        if (value == null || value.isBlank()) return SQLITE;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.equals("MONGO")) normalized = "MONGODB";
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported database type: " + value, exception);
        }
    }

    public boolean isJdbc() {
        return this == SQLITE || this == MYSQL;
    }
}
