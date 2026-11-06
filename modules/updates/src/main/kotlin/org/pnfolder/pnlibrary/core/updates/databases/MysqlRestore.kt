package org.pnfolder.pnlibrary.core.updates.databases

import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.util.zip.GZIPInputStream

/**
 * Puts a copy written by [MysqlDump] back: the tables named in the file are dropped and created again with their rows.
 * Tables the file does not mention stay as they are.
 */
internal class MysqlRestore {
    fun read(connection: Connection, source: Path) {
        connection.autoCommit = true
        connection.createStatement().use { statement ->
            statement.execute("SET SESSION sql_mode = REPLACE(@@sql_mode, 'NO_BACKSLASH_ESCAPES', '')")
            statement.execute("SET SESSION FOREIGN_KEY_CHECKS = 0")
            try {
                BufferedReader(InputStreamReader(GZIPInputStream(Files.newInputStream(source)), UTF_8)).use { reader ->
                    require(reader.readLine() == "-- pnlibrary-db 1") { "Это не копия базы pnLibrary" }
                    generateSequence { reader.readLine() }.filter { it.isNotBlank() }.forEach { statement.execute(it) }
                }
            } finally {
                statement.execute("SET SESSION FOREIGN_KEY_CHECKS = 1")
            }
        }
    }
}
