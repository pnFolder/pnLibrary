package org.pnfolder.pnlibrary.updates.databases.mysql

import org.pnfolder.pnlibrary.updates.databases.SqlRowWriter
import org.pnfolder.pnlibrary.updates.databases.TableMask
import org.pnfolder.pnlibrary.updates.databases.writeDumpFile
import java.io.BufferedWriter
import java.nio.file.Path
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Types

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
            writeDumpFile(target, HEADER) { out ->
                val rowWriter = SqlRowWriter(out, target, maximumBytes, ::value)
                tables(connection).filter { TableMask.matches(only, it) }.forEach { table -> dumpTable(connection, table, out, rowWriter) }
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

    private fun dumpTable(connection: Connection, table: String, out: BufferedWriter, rowWriter: SqlRowWriter) {
        val quoted = MysqlSyntax.identifier(table)
        out.write("DROP TABLE IF EXISTS $quoted;\n")
        out.write(createStatement(connection, quoted) + ";\n")
        connection.createStatement().use { statement ->
            statement.fetchSize = SqlRowWriter.FETCH_ROWS
            statement.executeQuery("SELECT * FROM $quoted").use { rows -> rowWriter.write(rows, "INSERT INTO $quoted VALUES (") }
        }
    }

    private fun createStatement(connection: Connection, quoted: String): String = connection.createStatement().use { statement ->
        statement.executeQuery("SHOW CREATE TABLE $quoted").use { rows ->
            check(rows.next()) { "Нет описания таблицы $quoted" }
            rows.getString(2).replace('\n', ' ')
        }
    }

    private fun value(rows: ResultSet, column: Int): String {
        val type = rows.metaData.getColumnType(column)
        if (type in SqlRowWriter.BINARY_TYPES) {
            val bytes = rows.getBytes(column) ?: return "NULL"
            return "X'" + bytes.joinToString("") { "%02x".format(it) } + "'"
        }
        val text = rows.getString(column) ?: return "NULL"
        return if (type in NUMBER_TYPES) text else MysqlSyntax.literal(text)
    }

    companion object {
        /** First line of every copy; the restore refuses a file without it. */
        const val HEADER = "-- pnlibrary-db 1"
        private val NUMBER_TYPES = setOf(
            Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT, Types.FLOAT, Types.REAL, Types.DOUBLE,
            Types.DECIMAL, Types.NUMERIC,
        )
    }
}
