package org.pnfolder.pnlibrary.core.updates.databases

import org.pnfolder.pnlibrary.spi.libraries.OptionalLibrary
import java.net.URLEncoder
import java.nio.file.Path

/**
 * MongoDB through its Java driver, which is loaded through Libby the first time it is needed. The driver's classes
 * are touched only by [MongoDump] and [MongoRestore], which run after the load.
 */
internal class MongoEngine(
    private val library: OptionalLibrary = OptionalLibrary("mongo", PROBE, MongoEngine::class.java.classLoader),
) : DatabaseEngine {
    private fun ensureDriver() {
        check(library.ensure()) { "Не удалось загрузить драйвер MongoDB: проверьте доступ сервера в интернет и повторите" }
    }

    override fun dump(connection: DatabaseConnection, target: Path) {
        ensureDriver()
        MongoDump().write(address(connection), connection.database, target)
    }

    override fun restore(connection: DatabaseConnection, source: Path) {
        ensureDriver()
        MongoRestore().read(address(connection), connection.database, source)
    }

    private fun address(connection: DatabaseConnection): String {
        connection.uri?.let { return it }
        val login = if (connection.user.isEmpty()) "" else "${encode(connection.user)}:${encode(connection.password)}@"
        return "mongodb://$login${connection.host}:${connection.port}/?authSource=${encode(connection.database)}"
    }

    private fun encode(text: String) = URLEncoder.encode(text, "UTF-8")

    private companion object {
        const val PROBE = "com.mongodb.client.MongoClients"
    }
}
