package org.pnfolder.pnlibrary.core.updates.databases

import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.Statement
import java.util.zip.GZIPInputStream

/**
 * Puts a copy of a SQL database back by running its statements, one per line: the tables the file names are
 * dropped and created again with their rows. Tables the file does not mention stay as they are.
 * @property header first line of a file this restore accepts
 * @property setup statements run before the file, such as relaxing foreign key checks
 * @property teardown statements run after the file, even when it fails
 * @property atomic run the whole file in one transaction that is rolled back on failure; only for databases with
 * transactional DDL (PostgreSQL), since MySQL commits implicitly at every `DROP TABLE`
 */
internal class SqlScriptRestore(
    private val header: String,
    private val setup: List<String> = emptyList(),
    private val teardown: List<String> = emptyList(),
    private val atomic: Boolean = false,
) {
    /** Runs the statements of [source] on [connection]; [teardown] runs even when a statement fails. */
    fun read(connection: Connection, source: Path) {
        connection.autoCommit = !atomic
        connection.createStatement().use { statement ->
            setup.forEach(statement::execute)
            try {
                run(connection, statement, source)
            } finally {
                teardown.forEach(statement::execute)
            }
        }
    }

    private fun run(connection: Connection, statement: Statement, source: Path) {
        try {
            BufferedReader(InputStreamReader(GZIPInputStream(Files.newInputStream(source)), UTF_8)).use { reader ->
                require(reader.readLine() == header) { "Это не копия базы pnLibrary для этого типа базы" }
                generateSequence { reader.readLine() }.filter { it.isNotBlank() }.forEach { statement.execute(it) }
            }
            if (atomic) connection.commit()
        } catch (error: Throwable) {
            if (atomic) runCatching { connection.rollback() }.onFailure(error::addSuppressed)
            throw error
        }
    }
}
