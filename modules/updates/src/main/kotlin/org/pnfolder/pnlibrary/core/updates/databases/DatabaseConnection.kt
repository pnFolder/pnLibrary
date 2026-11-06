package org.pnfolder.pnlibrary.core.updates.databases

import org.pnfolder.pnlibrary.api.config.DatabaseRole
import org.pnfolder.pnlibrary.common.io.TextFiles
import org.yaml.snakeyaml.Yaml
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files

/** How to reach a database, read from the plugin's own configuration file at the moment it is needed. */
class DatabaseConnection(val host: String, val port: Int, val database: String, val user: String, val password: String) {
    /** The password is left out so a connection never reaches a log by accident. */
    override fun toString(): String = "$user@$host:$port/$database"

    companion object {
        /** Names tried for a field that carries no `@DatabaseField`. */
        private val USUAL = mapOf(
            DatabaseRole.HOST to listOf("host", "hostname", "address"),
            DatabaseRole.PORT to listOf("port"),
            DatabaseRole.DATABASE to listOf("database", "db", "name"),
            DatabaseRole.USER to listOf("user", "username"),
            DatabaseRole.PASSWORD to listOf("password", "pass"),
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
            return DatabaseConnection(
                host = text(DatabaseRole.HOST) ?: missing(source, "host"),
                port = text(DatabaseRole.PORT)?.toIntOrNull() ?: 3306,
                database = text(DatabaseRole.DATABASE) ?: missing(source, "database"),
                user = text(DatabaseRole.USER) ?: missing(source, "user"),
                password = text(DatabaseRole.PASSWORD) ?: "",
            )
        }

        private fun missing(source: DatabaseSource, part: String): Nothing =
            error("Не нашёл «$part» для базы «${source.name}»: назовите поле так или пометьте его @DatabaseField")
    }
}
