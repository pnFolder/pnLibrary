package org.pnfolder.pnlibrary.updates.databases

import org.pnfolder.pnlibrary.updates.history.PrivateFiles
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.sql.ResultSet
import java.sql.Types
import java.util.zip.GZIPOutputStream

/** Opens the compressed copy at [target], writes [header] as its first line and lets [body] write the statements. */
internal fun writeDumpFile(target: Path, header: String, body: (BufferedWriter) -> Unit) {
    BufferedWriter(OutputStreamWriter(GZIPOutputStream(PrivateFiles.output(target)), UTF_8)).use { out ->
        out.write(header + "\n")
        body(out)
    }
}

/**
 * Writes result sets as multi-row `INSERT` statements, [ROWS_PER_INSERT] rows each, and fails once the copy at
 * [target] grows past [maximumBytes]. Each dialect supplies how one column becomes an SQL [literal].
 */
internal class SqlRowWriter(
    private val out: BufferedWriter,
    private val target: Path,
    private val maximumBytes: Long,
    private val literal: (ResultSet, Int) -> String,
) {
    /** Writes every row of [rows]; [insertHead] is the statement up to and including the opening parenthesis. */
    fun write(rows: ResultSet, insertHead: String) {
        val columns = rows.metaData.columnCount
        var inBatch = 0
        while (rows.next()) {
            out.write(if (inBatch == 0) insertHead else ",(")
            for (column in 1..columns) {
                if (column > 1) out.write(",")
                out.write(literal(rows, column))
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

    companion object {
        /** Rows the driver fetches at once while a table is read. */
        const val FETCH_ROWS = 1000
        private const val ROWS_PER_INSERT = 200

        /** Column types written as binary literals. */
        val BINARY_TYPES = setOf(Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB)
    }
}
