package org.pnfolder.pnlibrary.updates.databases.mongo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mongodb.client.model.CollationStrength;
import com.mongodb.client.model.IndexOptions;
import java.util.concurrent.TimeUnit;
import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;

class MongoRestoreTest {
    @Test
    void indexKeepsPartialFilterAndCollation() {
        BsonDocument index = BsonDocument.parse(
                "{\"name\":\"a_1\",\"key\":{\"a\":1},\"unique\":true,\"partialFilterExpression\":{\"a\":{\"$gt\":1}},\n"
                        + "\"collation\":{\"locale\":\"en\",\"strength\":2,\"caseLevel\":true,\"caseFirst\":\"off\"}}");
        IndexOptions options = MongoRestore.indexOptions(index);
        assertEquals("a_1", options.getName());
        assertTrue(options.isUnique());
        assertEquals(index.getDocument("partialFilterExpression"), options.getPartialFilterExpression().toBsonDocument());
        assertEquals("en", options.getCollation().getLocale());
        assertEquals(CollationStrength.SECONDARY, options.getCollation().getStrength());
        assertEquals(true, options.getCollation().getCaseLevel());
    }

    @Test
    void plainIndexHasNoExtras() {
        IndexOptions options = MongoRestore.indexOptions(BsonDocument.parse("{\"name\":\"b_1\",\"key\":{\"b\":1}}"));
        assertNull(options.getPartialFilterExpression());
        assertNull(options.getCollation());
        assertNull(options.getExpireAfter(TimeUnit.SECONDS));
    }
}
