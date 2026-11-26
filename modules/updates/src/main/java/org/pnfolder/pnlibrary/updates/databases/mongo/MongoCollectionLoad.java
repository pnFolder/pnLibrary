package org.pnfolder.pnlibrary.updates.databases.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import java.util.ArrayList;
import java.util.List;
import org.bson.BsonDocument;

/** The collection being filled: documents are inserted in batches and its indexes are built once it is full. */
final class MongoCollectionLoad {
    private static final int BATCH = 500;
    private static final String COLLECTION = "#collection ";
    private static final String INDEX = "#index ";

    private final MongoDatabase database;
    private MongoCollection<BsonDocument> collection;
    private final List<BsonDocument> indexes = new ArrayList<>();
    private final List<BsonDocument> batch = new ArrayList<>();

    MongoCollectionLoad(MongoDatabase database) {
        this.database = database;
    }

    void line(String line) {
        if (line.startsWith(COLLECTION)) {
            finish();
            String name = line.substring(COLLECTION.length());
            database.getCollection(name).drop();
            collection = database.getCollection(name, BsonDocument.class);
        } else if (line.startsWith(INDEX)) {
            indexes.add(BsonDocument.parse(line.substring(INDEX.length())));
        } else {
            batch.add(BsonDocument.parse(line));
            if (batch.size() >= BATCH) {
                flush();
            }
        }
    }

    void finish() {
        flush();
        if (collection != null) {
            for (BsonDocument index : indexes) {
                collection.createIndex(index.getDocument("key"), MongoRestore.indexOptions(index));
            }
        }
        indexes.clear();
    }

    private void flush() {
        if (batch.isEmpty()) {
            return;
        }
        if (collection != null) {
            collection.insertMany(new ArrayList<>(batch));
        }
        batch.clear();
    }
}
