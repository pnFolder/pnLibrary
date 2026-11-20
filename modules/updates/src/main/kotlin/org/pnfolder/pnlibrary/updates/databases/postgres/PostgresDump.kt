package org.pnfolder.pnlibrary.updates.databases.postgres

import org.pnfolder.pnlibrary.updates.databases.SqlRowWriter
import org.pnfolder.pnlibrary.updates.databases.TableMask
import org.pnfolder.pnlibrary.updates.databases.writeDumpFile
import java.io.BufferedWriter
import java.nio.file.Path
import java.sql.Connection
import java.sql.ResultSet

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
            writeDumpFile(target, HEADER) { out ->
                val rowWriter = SqlRowWriter(out, target, maximumBytes, ::value)
                val tables = schema.tables().filter { TableMask.matches(only, it) }
                // Dropping a table also drops the sequences it owns, so all drops come before the sequences are created.
                tables.forEach { out.write("DROP TABLE IF EXISTS ${PostgresSyntax.identifier(it)} CASCADE;\n") }
                schema.enumTypes(tables).forEach { out.write("$it;\n") }
                schema.sequences(only).forEach { out.write("$it;\n") }
                tables.forEach { dumpTable(connection, schema, it, out, rowWriter) }
                (tables.flatMap(schema::foreignKeys) + schema.externalForeignKeys(tables)).forEach { out.write("$it;\n") }
                schema.sequencePositions(only).forEach { out.write("$it;\n") }
            }
        } finally {
            connection.rollback()
        }
    }

    private fun dumpTable(connection: Connection, schema: PostgresSchema, table: String, out: BufferedWriter, rowWriter: SqlRowWriter) {
        val quoted = PostgresSyntax.identifier(table)
        out.write(schema.createTable(table) + ";\n")
        val columns = schema.columns(table).filterNot { it.generated }.map { PostgresSyntax.identifier(it.name) }
        if (columns.isNotEmpty()) {
            val list = columns.joinToString(", ")
            connection.createStatement().use { statement ->
                statement.fetchSize = SqlRowWriter.FETCH_ROWS
                statement.executeQuery("SELECT $list FROM $quoted").use { rows ->
                    rowWriter.write(rows, "INSERT INTO $quoted ($list) OVERRIDING SYSTEM VALUE VALUES (")
                }
            }
        }
        schema.indexes(table).forEach { out.write("$it;\n") }
    }

    private fun value(rows: ResultSet, column: Int): String {
        if (rows.metaData.getColumnType(column) in SqlRowWriter.BINARY_TYPES) {
            return rows.getBytes(column)?.let(PostgresSyntax::bytes) ?: "NULL"
        }
        return rows.getString(column)?.let(PostgresSyntax::literal) ?: "NULL"
    }

    companion object {
        /** First line of every copy; the restore refuses a file without it. */
        const val HEADER = "-- pnlibrary-pg 1"
    }
}
