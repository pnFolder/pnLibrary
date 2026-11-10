package org.pnfolder.pnlibrary.core.updates.databases

import org.pnfolder.pnlibrary.core.updates.history.PrivateFiles
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
 * Writes every table of a database into one compressed text file of SQL statements, one statement per line: the
 * table definition, then its rows. All tables are read in one consistent transaction, so rows match each other.
 */
internal class MysqlDump(private val maximumBytes: Long = 2L * 1024 * 1024 * 1024) {
    /** Writes the tables matching [only] (all when empty) to [target], failing past [maximumBytes]. */
    fun write(connection: Connection, only: List<String>, target: Path) {
        connection.autoCommit = false
        connection.createStatement().use { it.execute("START TRANSACTION WITH CONSISTENT SNAPSHOT") }
        try {
            BufferedWriter(OutputStreamWriter(GZIPOutputStream(PrivateFiles.output(target)), UTF_8)).use { out ->
                out.write(HEADER + "\n")
                tables(connection).filter { TableMask.matches(only, it) }.forEach { table -> dumpTable(connection, table, out, target) }
            }
        } finally {
            connection.rollback()
        }
    }

    private fun tables(connection: Connection): List<String> = connection.createStatement().use { statement ->
        statement.executeQuery("SHOW FULL TABLES WHERE Table_type = 'BASE TABLE'").use { rows ->
            generateSequence { if (rows.next()) rows.getString(1) else null }.toList()
        }
    }

    private fun dumpTable(connection: Connection, table: String, out: BufferedWriter, target: Path) {
        val quoted = MysqlSyntax.identifier(table)
        out.write("DROP TABLE IF EXISTS $quoted;\n")
        out.write(createStatement(connection, quoted) + ";\n")
        connection.createStatement().use { statement ->
            statement.fetchSize = FETCH_ROWS
            statement.executeQuery("SELECT * FROM $quoted").use { rows -> writeRows(rows, quoted, out, target) }
        }
    }

    private fun createStatement(connection: Connection, quoted: String): String = connection.createStatement().use { statement ->
        statement.executeQuery("SHOW CREATE TABLE $quoted").use { rows ->
            check(rows.next()) { "Нет описания таблицы $quoted" }
            rows.getString(2).replace('\n', ' ')
        }
    }

    private fun writeRows(rows: ResultSet, quoted: String, out: BufferedWriter, target: Path) {
        val columns = rows.metaData.columnCount
        var inBatch = 0
        var batchChars = 0
        while (rows.next()) {
            out.write(if (inBatch == 0) "INSERT INTO $quoted VALUES (" else ",(")
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
        val type = rows.metaData.getColumnType(column)
        if (type in BINARY_TYPES) {
            val bytes = rows.getBytes(column) ?: return "NULL"
            return "X'" + bytes.joinToString("") { "%02x".format(it) } + "'"
        }
        val text = rows.getString(column) ?: return "NULL"
        return if (type in NUMBER_TYPES) text else MysqlSyntax.literal(text)
    }

    companion object {
        /** First line of every copy; the restore refuses a file without it. */
        const val HEADER = "-- pnlibrary-db 1"

        private const val FETCH_ROWS = 1000
        private const val ROWS_PER_INSERT = 200
        private const val MAX_INSERT_CHARS = 1_000_000
        private val BINARY_TYPES = setOf(Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB)
        private val NUMBER_TYPES = setOf(
            Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT, Types.FLOAT, Types.REAL, Types.DOUBLE,
            Types.DECIMAL, Types.NUMERIC,
        )
    }
}
