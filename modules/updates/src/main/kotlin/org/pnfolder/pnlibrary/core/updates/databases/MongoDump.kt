package org.pnfolder.pnlibrary.core.updates.databases

import com.mongodb.client.MongoClients
import org.bson.BsonDocument
import org.bson.json.JsonMode
import org.bson.json.JsonWriterSettings
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPOutputStream

/**
 * Writes every collection of a database into one compressed text file: a `#collection` line, its indexes as
 * `#index` lines, then one document per line in extended JSON, which keeps every value's type. MongoDB gives no
 * snapshot across collections, so a plugin that writes during the copy can leave collections a moment apart.
 */
internal class MongoDump(private val maximumBytes: Long = 2L * 1024 * 1024 * 1024) {
    fun write(address: String, database: String, only: List<String>, target: Path) {
        val json = JsonWriterSettings.builder().outputMode(JsonMode.EXTENDED).build()
        MongoClients.create(address).use { client ->
            val db = client.getDatabase(database)
            BufferedWriter(OutputStreamWriter(GZIPOutputStream(Files.newOutputStream(target)), UTF_8)).use { out ->
                out.write("-- pnlibrary-mongo 1\n")
                db.listCollectionNames().filterNot { it.startsWith("system.") || !TableMask.matches(only, it) }.forEach { name ->
                    require(name.none { it == '\n' || it == '\r' }) { "Имя коллекции с переводом строки не поддерживается" }
                    out.write("#collection $name\n")
                    val collection = db.getCollection(name, BsonDocument::class.java)
                    collection.listIndexes(BsonDocument::class.java).filterNot { it.getString("name").value == "_id_" }
                        .forEach { out.write("#index ${it.toJson(json)}\n") }
                    var written = 0
                    collection.find().batchSize(BATCH).forEach { document ->
                        out.write(document.toJson(json) + "\n")
                        if (++written % BATCH == 0) {
                            out.flush()
                            check(Files.size(target) <= maximumBytes) { "Копия базы больше ${maximumBytes / (1024 * 1024)} МБ" }
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val BATCH = 1000
    }
}
