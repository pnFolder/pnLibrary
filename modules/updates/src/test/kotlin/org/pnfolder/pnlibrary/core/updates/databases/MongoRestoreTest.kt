package org.pnfolder.pnlibrary.core.updates.databases

import com.mongodb.client.model.CollationStrength
import org.bson.BsonDocument
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MongoRestoreTest {
    @Test
    fun `index keeps partial filter and collation`() {
        val index = BsonDocument.parse(
            """{"name":"a_1","key":{"a":1},"unique":true,"partialFilterExpression":{"a":{"${'$'}gt":1}},
            "collation":{"locale":"en","strength":2,"caseLevel":true,"caseFirst":"off"}}""",
        )
        val options = MongoRestore.indexOptions(index)
        assertEquals("a_1", options.name)
        assertTrue(options.isUnique)
        assertEquals(index.getDocument("partialFilterExpression"), options.partialFilterExpression!!.toBsonDocument())
        assertEquals("en", options.collation!!.locale)
        assertEquals(CollationStrength.SECONDARY, options.collation!!.strength)
        assertEquals(true, options.collation!!.caseLevel)
    }

    @Test
    fun `plain index has no extras`() {
        val options = MongoRestore.indexOptions(BsonDocument.parse("""{"name":"b_1","key":{"b":1}}"""))
        assertNull(options.partialFilterExpression)
        assertNull(options.collation)
        assertNull(options.getExpireAfter(java.util.concurrent.TimeUnit.SECONDS))
    }
}
