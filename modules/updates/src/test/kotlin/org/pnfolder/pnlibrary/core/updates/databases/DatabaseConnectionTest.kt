package org.pnfolder.pnlibrary.core.updates.databases

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pnfolder.pnlibrary.api.config.DatabaseKind
import org.pnfolder.pnlibrary.api.config.DatabaseRole
import java.nio.file.Files
import java.nio.file.Path

class DatabaseConnectionTest {
    @TempDir
    lateinit var directory: Path

    private fun source(yaml: String, path: String, keys: Map<DatabaseRole, String> = emptyMap(), name: String = "Кланы"): DatabaseSource {
        val file = directory.resolve("config.yml")
        Files.write(file, yaml.toByteArray())
        return DatabaseSource("pnclans", DatabaseKind.MYSQL, name, file, path, keys)
    }

    @Test
    fun `usual names are found and the port defaults to 3306`() {
        val connection = DatabaseConnection.read(source("storage:\n  host: db.local\n  database: clans\n  username: root\n  password: pw\n", "storage"))
        assertEquals("root@db.local:3306/clans", connection.toString())
        assertEquals("pw", connection.password)
    }

    @Test
    fun `fields with other names are read through their roles`() {
        val keys = mapOf(
            DatabaseRole.HOST to "server-address", DatabaseRole.PORT to "tcp", DatabaseRole.DATABASE to "schema",
            DatabaseRole.USER to "login", DatabaseRole.PASSWORD to "secret",
        )
        val yaml = "mysql:\n  server-address: 10.0.0.5\n  tcp: 3307\n  schema: s\n  login: u\n  secret: p\n"
        assertEquals("u@10.0.0.5:3307/s", DatabaseConnection.read(source(yaml, "mysql", keys)).toString())
    }

    @Test
    fun `a root level connection has an empty path`() {
        assertEquals("u@h:3306/d", DatabaseConnection.read(source("host: h\ndatabase: d\nuser: u\n", "")).toString())
    }

    @Test
    fun `two databases of one file do not get mixed up`() {
        val yaml = "clans:\n  host: a\n  database: one\n  user: u\nbank:\n  host: b\n  database: two\n  user: u\n"
        assertEquals("u@a:3306/one", DatabaseConnection.read(source(yaml, "clans", name = "Кланы")).toString())
        assertEquals("u@b:3306/two", DatabaseConnection.read(source(yaml, "bank", name = "Банк")).toString())
    }

    @Test
    fun `a missing part is reported with the database name`() {
        val error = assertThrows(IllegalStateException::class.java) { DatabaseConnection.read(source("storage:\n  host: h\n", "storage")) }
        assertEquals(true, error.message!!.contains("Кланы"))
    }

    @Test
    fun `a mongo connection may be a single connection string or parts without a user`() {
        fun mongo(yaml: String) = source(yaml, "mongo").let { DatabaseSource(it.plugin, DatabaseKind.MONGODB, it.name, it.file, it.path, it.keys) }
        assertEquals("uri/clans", DatabaseConnection.read(mongo("mongo:\n  uri: mongodb://u:p@h/clans\n  database: clans\n")).toString())
        assertEquals("@h:27017/clans", DatabaseConnection.read(mongo("mongo:\n  host: h\n  database: clans\n")).toString())
    }
}
