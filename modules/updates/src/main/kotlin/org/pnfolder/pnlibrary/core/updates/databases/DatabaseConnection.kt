package org.pnfolder.pnlibrary.core.updates.databases

import org.pnfolder.pnlibrary.api.config.DatabaseKind
import org.pnfolder.pnlibrary.api.config.DatabaseRole
import org.pnfolder.pnlibrary.common.io.TextFiles
import org.yaml.snakeyaml.Yaml
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files

/**
 * How to reach a database, read from the plugin's own configuration file at the moment it is needed.
 * @property uri a full connection string, when the plugin keeps one; it replaces host, port, user and password
 * @property tables the tables or collections to copy, empty for all
 */
class DatabaseConnection(
    val host: String,
    val port: Int,
    val database: String,
    val user: String,
    val password: String,
    val uri: String? = null,
    val tables: List<String> = emptyList(),
) {
    /** The password is left out so a connection never reaches a log by accident. */
    override fun toString(): String = if (uri != null) "uri/$database" else "$user@$host:$port/$database"

    companion object {
        /** Names tried for a field that carries no `@DatabaseField`. */
        private val USUAL = mapOf(
            DatabaseRole.HOST to listOf("host", "hostname", "address"),
            DatabaseRole.PORT to listOf("port"),
            DatabaseRole.DATABASE to listOf("database", "db", "name"),
            DatabaseRole.USER to listOf("user", "username"),
            DatabaseRole.PASSWORD to listOf("password", "pass"),
            DatabaseRole.URI to listOf("uri", "url", "connection-string"),
        )

        fun read(source: DatabaseSource): DatabaseConnection {
            require(Files.isRegularFile(source.file)) { "Файл настроек ${source.file.fileName} не найден" }
            var node: Any? = Yaml().load<Any?>(TextFiles.read(source.file, UTF_8))
            source.path.split('.').filter(String::isNotEmpty).forEach { node = (node as? Map<*, *>)?.get(it) }
            val section = node as? Map<*, *> ?: error("В ${source.file.fileName} нет раздела «${source.path}» с подключением к базе")

            fun text(role: DatabaseRole): String? {
                val keys = source.keys[role]?.let(::listOf) ?: USUAL.getValue(role)
                return keys.firstNotNullOfOrNull { section[it]?.toString()?.takeIf(String::isNotBlank) }
            }
            val mongo = source.kind == DatabaseKind.MONGODB
            val uri = if (mongo) text(DatabaseRole.URI) else null
            val database = text(DatabaseRole.DATABASE) ?: missing(source, "database")
            val tables = source.tables + source.tableKeys.mapNotNull { section[it]?.toString()?.takeIf(String::isNotBlank) }
            check(tables.isNotEmpty() || source.tableKeys.isEmpty()) { "В поле @BackupTable базы «${source.name}» нет имени таблицы" }
            if (uri != null) return DatabaseConnection("", 0, database, "", "", uri, tables)
            return DatabaseConnection(
                host = text(DatabaseRole.HOST) ?: missing(source, "host"),
                port = text(DatabaseRole.PORT)?.toIntOrNull() ?: when (source.kind) {
                    DatabaseKind.MONGODB -> 27017
                    DatabaseKind.POSTGRESQL -> 5432
                    DatabaseKind.MYSQL -> 3306
                },
                database = database,
                user = text(DatabaseRole.USER) ?: if (mongo) "" else missing(source, "user"),
                password = text(DatabaseRole.PASSWORD) ?: "",
                tables = tables,
            )
        }

        private fun missing(source: DatabaseSource, part: String): Nothing =
            error("Не нашёл «$part» для базы «${source.name}»: назовите поле так или пометьте его @DatabaseField")
    }
}
