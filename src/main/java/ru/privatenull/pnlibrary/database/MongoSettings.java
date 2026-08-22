package ru.privatenull.pnlibrary.database;

public record MongoSettings(String uri, String database, String collection) implements DatabaseSettings {
    public MongoSettings {
        if (uri == null || uri.isBlank()) throw new IllegalArgumentException("MongoDB URI cannot be blank");
        if (database == null || database.isBlank()) throw new IllegalArgumentException("MongoDB database cannot be blank");
        if (collection == null || collection.isBlank()) throw new IllegalArgumentException("MongoDB collection cannot be blank");
        uri = uri.trim();
        database = database.trim();
        collection = collection.trim();
    }

    @Override
    public DatabaseType type() {
        return DatabaseType.MONGODB;
    }
}
