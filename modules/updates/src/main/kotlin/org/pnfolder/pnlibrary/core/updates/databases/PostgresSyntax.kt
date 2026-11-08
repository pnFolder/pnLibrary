package org.pnfolder.pnlibrary.core.updates.databases

/** Quoting for the statements a copy of a PostgreSQL database is made of. */
internal object PostgresSyntax {
    /** [name] quoted as a PostgreSQL identifier. */
    fun identifier(name: String): String = "\"" + name.replace("\"", "\"\"") + "\""

    /**
     * A text literal on one line. The E prefix lets line breaks and backslashes be written as escapes, and a quote is
     * doubled; PostgreSQL turns the text into the column's type, so one form fits every column.
     */
    fun literal(text: String): String = buildString(text.length + 3) {
        append("E'")
        for (char in text) {
            when (char) {
                '\\' -> append("\\\\")
                '\'' -> append("''")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> append(char)
            }
        }
        append('\'')
    }

    /** Binary [value] as a hex literal. */
    fun bytes(value: ByteArray): String = "'\\x" + value.joinToString("") { "%02x".format(it) } + "'"
}
