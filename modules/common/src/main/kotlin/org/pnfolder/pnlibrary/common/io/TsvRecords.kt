package org.pnfolder.pnlibrary.common.io

import java.nio.file.Files
import java.nio.file.Path

/**
 * The one way the library keeps a table of records on disk: a header line with the format name and version, then one
 * line per record with the fields separated by tabs. Tab, line break and backslash inside a field are escaped, a file
 * is replaced atomically, and a file that is missing or damaged reads as fewer records instead of an error.
 */
object TsvRecords {
    private const val TAB = "\t"
    private const val BACKSLASH = '\\'

    /** The records of [path] without its header line; empty when the file is missing or cannot be read. */
    fun read(path: Path): List<List<String>> {
        if (!Files.isRegularFile(path)) return emptyList()
        return runCatching {
            Files.readAllLines(path, Charsets.UTF_8).drop(1).filter { it.isNotEmpty() }.map { line -> line.split(TAB).map(::unescape) }
        }.getOrDefault(emptyList())
    }

    /** Replaces [path] with [header] and [records]; a `null` field is written empty. Returns whether the file was written. */
    fun write(path: Path, header: String, records: List<List<Any?>>): Boolean {
        val lines = listOf(header) + records.map { fields -> fields.joinToString(TAB) { escape(it?.toString().orEmpty()) } }
        return runCatching {
            path.parent?.let { Files.createDirectories(it) }
            AtomicFiles.write(path, lines.joinToString("\n", postfix = "\n").toByteArray(Charsets.UTF_8))
        }.isSuccess
    }

    /** [value] with backslash, tab and line break written as `\\`, `\t` and `\n`; carriage returns are dropped. */
    fun escape(value: String): String = buildString {
        for (char in value) {
            when (char) {
                BACKSLASH -> append(BACKSLASH).append(BACKSLASH)
                '\t' -> append(BACKSLASH).append('t')
                '\n' -> append(BACKSLASH).append('n')
                '\r' -> Unit
                else -> append(char)
            }
        }
    }

    /** The reverse of [escape]. */
    fun unescape(value: String): String {
        if (BACKSLASH !in value) return value
        val out = StringBuilder()
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char == BACKSLASH && index + 1 < value.length) {
                index++
                out.append(
                    when (value[index]) {
                        't' -> '\t'
                        'n' -> '\n'
                        else -> value[index]
                    },
                )
            } else {
                out.append(char)
            }
            index++
        }
        return out.toString()
    }
}
