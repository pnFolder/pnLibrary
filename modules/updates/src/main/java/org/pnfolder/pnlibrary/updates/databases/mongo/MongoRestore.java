package org.pnfolder.pnlibrary.updates.databases.mongo;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.model.Collation;
import com.mongodb.client.model.CollationAlternate;
import com.mongodb.client.model.CollationCaseFirst;
import com.mongodb.client.model.CollationMaxVariable;
import com.mongodb.client.model.CollationStrength;
import com.mongodb.client.model.IndexOptions;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.updates.databases.Blank;

/** Puts a copy written by {@link MongoDump} back: collections named in the file are dropped and filled again, with their indexes. */
public final class MongoRestore {
    /** Reads {@code source} into {@code database}; only the collections the file names are touched. */
    public void read(@NotNull String address, @NotNull String database, @NotNull Path source) throws IOException {
        try (MongoClient client = MongoClients.create(address);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(new GZIPInputStream(Files.newInputStream(source)), StandardCharsets.UTF_8))) {
            if (!MongoDump.HEADER.equals(reader.readLine())) {
                throw new IllegalArgumentException("Это не копия базы MongoDB от pnLibrary");
            }
            MongoCollectionLoad load = new MongoCollectionLoad(client.getDatabase(database));
            String line;
            while ((line = reader.readLine()) != null) {
                if (!Blank.is(line)) {
                    load.line(line);
                }
            }
            load.finish();
        }
    }

    /** Index options written by {@link MongoDump}: name, uniqueness, TTL, partial filter and collation are kept. */
    @NotNull
    public static IndexOptions indexOptions(@NotNull BsonDocument index) {
        IndexOptions options = new IndexOptions().name(index.getString("name").getValue());
        if (index.getBoolean("unique", BsonBoolean.FALSE).getValue()) {
            options.unique(true);
        }
        if (index.getBoolean("sparse", BsonBoolean.FALSE).getValue()) {
            options.sparse(true);
        }
        if (index.containsKey("expireAfterSeconds")) {
            options.expireAfter(index.getNumber("expireAfterSeconds").longValue(), TimeUnit.SECONDS);
        }
        if (index.containsKey("partialFilterExpression")) {
            options.partialFilterExpression(index.getDocument("partialFilterExpression"));
        }
        if (index.containsKey("collation")) {
            options.collation(collationOf(index.getDocument("collation")));
        }
        return options;
    }

    private static Collation collationOf(BsonDocument document) {
        Collation.Builder builder = Collation.builder().locale(document.getString("locale").getValue());
        Boolean caseLevel = optionalBoolean(document, "caseLevel");
        if (caseLevel != null) {
            builder.caseLevel(caseLevel);
        }
        Boolean numericOrdering = optionalBoolean(document, "numericOrdering");
        if (numericOrdering != null) {
            builder.numericOrdering(numericOrdering);
        }
        Boolean backwards = optionalBoolean(document, "backwards");
        if (backwards != null) {
            builder.backwards(backwards);
        }
        Boolean normalization = optionalBoolean(document, "normalization");
        if (normalization != null) {
            builder.normalization(normalization);
        }
        String caseFirst = optionalString(document, "caseFirst");
        if (caseFirst != null) {
            builder.collationCaseFirst(CollationCaseFirst.fromString(caseFirst));
        }
        String alternate = optionalString(document, "alternate");
        if (alternate != null) {
            builder.collationAlternate(CollationAlternate.fromString(alternate));
        }
        String maxVariable = optionalString(document, "maxVariable");
        if (maxVariable != null) {
            builder.collationMaxVariable(CollationMaxVariable.fromString(maxVariable));
        }
        if (document.isNumber("strength")) {
            builder.collationStrength(CollationStrength.fromInt(document.getNumber("strength").intValue()));
        }
        return builder.build();
    }

    private static Boolean optionalBoolean(BsonDocument document, String key) {
        BsonValue value = document.get(key);
        return value != null && value.isBoolean() ? Boolean.valueOf(value.asBoolean().getValue()) : null;
    }

    private static String optionalString(BsonDocument document, String key) {
        BsonValue value = document.get(key);
        return value != null && value.isString() ? value.asString().getValue() : null;
    }
}
