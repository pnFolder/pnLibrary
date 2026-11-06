package org.pnfolder.pnlibrary.core.updates.databases

import com.mongodb.client.MongoClients
import com.mongodb.client.MongoCollection
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
    fun read(address: String, database: String, source: Path) {
        MongoClients.create(address).use { client ->
            val db = client.getDatabase(database)
            BufferedReader(InputStreamReader(GZIPInputStream(Files.newInputStream(source)), UTF_8)).use { reader ->
                require(reader.readLine() == "-- pnlibrary-mongo 1") { "Это не копия базы MongoDB от pnLibrary" }
                var collection: MongoCollection<BsonDocument>? = null
                val indexes = mutableListOf<BsonDocument>()
                val batch = mutableListOf<BsonDocument>()

                fun flush() {
                    if (batch.isEmpty()) return
                    collection?.insertMany(batch.toList())
                    batch.clear()
                }

                fun finish() {
                    flush()
                    collection?.let { target -> indexes.forEach { createIndex(target, it) } }
                    indexes.clear()
                }
                generateSequence { reader.readLine() }.filter { it.isNotBlank() }.forEach { line ->
                    when {
                        line.startsWith("#collection ") -> {
                            finish()
                            val name = line.removePrefix("#collection ")
                            db.getCollection(name).drop()
                            collection = db.getCollection(name, BsonDocument::class.java)
                        }
                        line.startsWith("#index ") -> indexes += BsonDocument.parse(line.removePrefix("#index "))
                        else -> {
                            batch += BsonDocument.parse(line)
                            if (batch.size >= BATCH) flush()
                        }
                    }
                }
                finish()
            }
        }
    }

    private fun createIndex(collection: MongoCollection<BsonDocument>, index: BsonDocument) {
        val options = IndexOptions().name(index.getString("name").value)
        if (index.getBoolean("unique", BsonBoolean.FALSE).value) options.unique(true)
        if (index.getBoolean("sparse", BsonBoolean.FALSE).value) options.sparse(true)
        if (index.containsKey("expireAfterSeconds")) {
            options.expireAfter(index.getNumber("expireAfterSeconds").longValue(), TimeUnit.SECONDS)
        }
        collection.createIndex(index.getDocument("key"), options)
    }

    private companion object {
        const val BATCH = 500
    }
}
