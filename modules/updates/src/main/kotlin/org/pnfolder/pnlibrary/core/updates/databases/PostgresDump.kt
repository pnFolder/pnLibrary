package org.pnfolder.pnlibrary.core.updates.databases

import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Types
import java.util.zip.GZIPOutputStream

/**
 * Writes the tables of the current schema into one compressed text file of SQL statements, one per line: sequences,
 * then each table with its rows and indexes, then foreign keys and sequence positions. Every table is read in one
 * consistent read-only transaction. Views, functions, triggers and other schemas are not copied.
 */
internal class PostgresDump(private val maximumBytes: Long = 2L * 1024 * 1024 * 1024) {
    /** Writes the tables matching [only] (all when empty) to [target], failing past [maximumBytes]. */
    fun write(connection: Connection, only: List<String>, target: Path) {
        connection.autoCommit = false
        connection.createStatement().use { it.execute("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY") }
        try {
            val schema = PostgresSchema(connection)
            BufferedWriter(OutputStreamWriter(GZIPOutputStream(Files.newOutputStream(target)), UTF_8)).use { out ->
                out.write(HEADER + "\n")
                val tables = schema.tables().filter { TableMask.matches(only, it) }
                // Dropping a table also drops the sequences it owns, so all drops come before the sequences are created.
                tables.forEach { out.write("DROP TABLE IF EXISTS ${PostgresSyntax.identifier(it)} CASCADE;\n") }
                schema.sequences(only).forEach { out.write("CREATE SEQUENCE IF NOT EXISTS ${PostgresSyntax.identifier(it)};\n") }
                tables.forEach { dumpTable(connection, schema, it, out, target) }
                tables.flatMap(schema::foreignKeys).forEach { out.write("$it;\n") }
                schema.sequencePositions(only).forEach { out.write("$it;\n") }
            }
        } finally {
            connection.rollback()
        }
    }

    private fun dumpTable(connection: Connection, schema: PostgresSchema, table: String, out: BufferedWriter, target: Path) {
        val quoted = PostgresSyntax.identifier(table)
        out.write(schema.createTable(table) + ";\n")
        val columns = schema.columns(table).filterNot { it.generated }.map { PostgresSyntax.identifier(it.name) }
        if (columns.isNotEmpty()) {
            val list = columns.joinToString(", ")
            connection.createStatement().use { statement ->
                statement.fetchSize = FETCH_ROWS
                statement.executeQuery("SELECT $list FROM $quoted").use { rows -> writeRows(rows, "$quoted ($list)", out, target) }
            }
        }
        schema.indexes(table).forEach { out.write("$it;\n") }
    }

    private fun writeRows(rows: ResultSet, into: String, out: BufferedWriter, target: Path) {
        val columns = rows.metaData.columnCount
        var inBatch = 0
        while (rows.next()) {
            out.write(if (inBatch == 0) "INSERT INTO $into OVERRIDING SYSTEM VALUE VALUES (" else ",(")
            for (column in 1..columns) {
                if (column > 1) out.write(",")
                out.write(value(rows, column))
            }
            out.write(")")
            if (++inBatch == ROWS_PER_INSERT) {
                out.write(";\n")
                inBatch = 0
                out.flush()
                check(Files.size(target) <= maximumBytes) { "Копия базы больше ${maximumBytes / (1024 * 1024)} МБ" }
            }
        }
        if (inBatch > 0) out.write(";\n")
    }

    private fun value(rows: ResultSet, column: Int): String {
        if (rows.metaData.getColumnType(column) in BINARY_TYPES) {
            return rows.getBytes(column)?.let(PostgresSyntax::bytes) ?: "NULL"
        }
        return rows.getString(column)?.let(PostgresSyntax::literal) ?: "NULL"
    }

    companion object {
        /** First line of every copy; the restore refuses a file without it. */
        const val HEADER = "-- pnlibrary-pg 1"
        private const val FETCH_ROWS = 1000
        private const val ROWS_PER_INSERT = 200
        private val BINARY_TYPES = setOf(Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB)
    }
}
