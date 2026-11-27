package org.pnfolder.pnlibrary.updates.databases.mongo;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import org.bson.BsonDocument;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.updates.databases.dump.TableMask;
import org.pnfolder.pnlibrary.updates.history.backup.PrivateFiles;

/**
 * Writes every collection of a database into one compressed text file: a {@code #collection} line, its indexes as
 * {@code #index} lines, then one document per line in extended JSON, which keeps every value's type. MongoDB gives no
 * snapshot across collections, so a plugin that writes during the copy can leave collections a moment apart.
 */
public final class MongoDump {
    /** First line of every copy; the restore refuses a file without it. */
    public static final String HEADER = "-- pnlibrary-mongo 1";

    private static final int BATCH = 1000;

    private final long maximumBytes;

    public MongoDump() {
        this(2L * 1024 * 1024 * 1024);
    }

    public MongoDump(long maximumBytes) {
        this.maximumBytes = maximumBytes;
    }

    /** Writes the collections of {@code database} matching {@code only} (all when empty) to {@code target}, failing past the maximum size. */
    public void write(@NotNull String address, @NotNull String database, @NotNull List<String> only, @NotNull Path target)
            throws IOException {
        JsonWriterSettings json = JsonWriterSettings.builder().outputMode(JsonMode.EXTENDED).build();
        try (MongoClient client = MongoClients.create(address)) {
            MongoDatabase db = client.getDatabase(database);
            try (BufferedWriter out = new BufferedWriter(
                    new OutputStreamWriter(new GZIPOutputStream(PrivateFiles.output(target)), StandardCharsets.UTF_8))) {
                out.write(HEADER + "\n");
                for (String name : db.listCollectionNames()) {
                    if (!name.startsWith("system.") && TableMask.matches(only, name)) {
                        writeCollection(db, name, json, out, target);
                    }
                }
            }
        }
    }

    private void writeCollection(MongoDatabase db, String name, JsonWriterSettings json, BufferedWriter out, Path target)
            throws IOException {
        if (name.indexOf('\n') >= 0 || name.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Имя коллекции с переводом строки не поддерживается");
        }
        out.write("#collection " + name + "\n");
        MongoCollection<BsonDocument> collection = db.getCollection(name, BsonDocument.class);
        for (BsonDocument index : collection.listIndexes(BsonDocument.class)) {
            if (!"_id_".equals(index.getString("name").getValue())) {
                out.write("#index " + index.toJson(json) + "\n");
            }
        }
        int written = 0;
        for (BsonDocument document : collection.find().batchSize(BATCH)) {
            out.write(document.toJson(json) + "\n");
            if (++written % BATCH == 0) {
                out.flush();
                if (Files.size(target) > maximumBytes) {
                    throw new IllegalStateException("Копия базы больше " + maximumBytes / (1024 * 1024) + " МБ");
                }
            }
        }
    }
}
