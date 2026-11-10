package org.pnfolder.pnlibrary.core.updates.databases

import com.mongodb.client.MongoClients
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Collation
import com.mongodb.client.model.CollationAlternate
import com.mongodb.client.model.CollationCaseFirst
import com.mongodb.client.model.CollationMaxVariable
import com.mongodb.client.model.CollationStrength
import com.mongodb.client.model.IndexOptions
import org.bson.BsonBoolean
import org.bson.BsonDocument
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/** Puts a copy written by [MongoDump] back: collections named in the file are dropped and filled again, with their indexes. */
internal class MongoRestore {
    /** Reads [source] into [database]; only the collections the file names are touched. */
    fun read(address: String, database: String, source: Path) {
        MongoClients.create(address).use { client ->
            BufferedReader(InputStreamReader(GZIPInputStream(Files.newInputStream(source)), UTF_8)).use { reader ->
                require(reader.readLine() == MongoDump.HEADER) { "Это не копия базы MongoDB от pnLibrary" }
                val load = CollectionLoad(client.getDatabase(database))
                generateSequence { reader.readLine() }.filter { it.isNotBlank() }.forEach(load::line)
                load.finish()
            }
        }
    }

    /** The collection being filled: documents are inserted in batches and its indexes are built once it is full. */
    private class CollectionLoad(private val database: MongoDatabase) {
        private var collection: MongoCollection<BsonDocument>? = null
        private val indexes = mutableListOf<BsonDocument>()
        private val batch = mutableListOf<BsonDocument>()

        fun line(line: String) {
            when {
                line.startsWith("#collection ") -> {
                    finish()
                    val name = line.removePrefix("#collection ")
                    database.getCollection(name).drop()
                    collection = database.getCollection(name, BsonDocument::class.java)
                }
                line.startsWith("#index ") -> indexes += BsonDocument.parse(line.removePrefix("#index "))
                else -> {
                    batch += BsonDocument.parse(line)
                    if (batch.size >= BATCH) flush()
                }
            }
        }

        fun finish() {
            flush()
            collection?.let { target -> indexes.forEach { createIndex(target, it) } }
            indexes.clear()
        }

        private fun flush() {
            if (batch.isEmpty()) return
            collection?.insertMany(batch.toList())
            batch.clear()
        }

        private fun createIndex(collection: MongoCollection<BsonDocument>, index: BsonDocument) {
            collection.createIndex(index.getDocument("key"), indexOptions(index))
        }
    }

    internal companion object {
        private const val BATCH = 500

        /** Index options written by [MongoDump]: name, uniqueness, TTL, partial filter and collation are kept. */
        fun indexOptions(index: BsonDocument): IndexOptions {
            val options = IndexOptions().name(index.getString("name").value)
            if (index.getBoolean("unique", BsonBoolean.FALSE).value) options.unique(true)
            if (index.getBoolean("sparse", BsonBoolean.FALSE).value) options.sparse(true)
            if (index.containsKey("expireAfterSeconds")) {
                options.expireAfter(index.getNumber("expireAfterSeconds").longValue(), TimeUnit.SECONDS)
            }
            if (index.containsKey("partialFilterExpression")) {
                options.partialFilterExpression(index.getDocument("partialFilterExpression"))
            }
            if (index.containsKey("collation")) options.collation(collationOf(index.getDocument("collation")))
            return options
        }

        private fun collationOf(document: BsonDocument): Collation {
            val builder = Collation.builder().locale(document.getString("locale").value)
            document.optionalBoolean("caseLevel")?.let(builder::caseLevel)
            document.optionalBoolean("numericOrdering")?.let(builder::numericOrdering)
            document.optionalBoolean("backwards")?.let(builder::backwards)
            document.optionalBoolean("normalization")?.let(builder::normalization)
            document.optionalString("caseFirst")?.let { builder.collationCaseFirst(CollationCaseFirst.fromString(it)) }
            document.optionalString("alternate")?.let { builder.collationAlternate(CollationAlternate.fromString(it)) }
            document.optionalString("maxVariable")?.let { builder.collationMaxVariable(CollationMaxVariable.fromString(it)) }
            if (document.isNumber("strength")) {
                builder.collationStrength(CollationStrength.fromInt(document.getNumber("strength").intValue()))
            }
            return builder.build()
        }

        private fun BsonDocument.optionalBoolean(key: String): Boolean? = if (get(key)?.isBoolean == true) getBoolean(key).value else null

        private fun BsonDocument.optionalString(key: String): String? = if (get(key)?.isString == true) getString(key).value else null
    }
}
