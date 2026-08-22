package ru.privatenull.pnlibrary.database;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoCollection;
import org.bson.Document;

/** Owns the Mongo client lifecycle while plugins remain responsible for their collections and indexes. */
public final class MongoDatabaseManager implements AutoCloseable {
    private final MongoSettings settings;
    private MongoClient client;
    private MongoDatabase database;

    public MongoDatabaseManager(MongoSettings settings) {
        this.settings = settings;
    }

    public synchronized void open() {
        if (client != null) return;
        client = MongoClients.create(settings.uri());
        database = client.getDatabase(settings.database());
        database.runCommand(new org.bson.Document("ping", 1));
    }

    public MongoDatabase database() {
        MongoDatabase current = database;
        if (current == null) throw new IllegalStateException("MongoDB database is not open");
        return current;
    }

    public MongoClient client() {
        MongoClient current = client;
        if (current == null) throw new IllegalStateException("MongoDB client is not open");
        return current;
    }

    public MongoSettings settings() {
        return settings;
    }

    /** Returns a namespaced collection based on the configured collection prefix. */
    public MongoCollection<Document> collection(String suffix) {
        String clean = suffix == null ? "" : suffix.toLowerCase().replaceAll("[^a-z0-9_-]", "_");
        String name = clean.isBlank() ? settings.collection() : settings.collection() + "_" + clean;
        return database().getCollection(name);
    }

    @Override
    public synchronized void close() {
        if (client != null) client.close();
        client = null;
        database = null;
    }
}
