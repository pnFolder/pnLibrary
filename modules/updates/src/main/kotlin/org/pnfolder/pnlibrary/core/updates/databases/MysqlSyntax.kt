package org.pnfolder.pnlibrary.core.updates.databases

/** Quoting for the statements a copy of a MySQL database is made of. */
internal object MysqlSyntax {
    fun identifier(name: String): String = "`" + name.replace("`", "``") + "`"

    /** A text literal on one line: every character that could end or change the statement is escaped. */
    fun literal(text: String): String = buildString(text.length + 2) {
        append('\'')
        for (char in text) {
            when (char) {
                '\\' -> append("\\\\")
                '\'' -> append("\\'")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\u0000' -> append("\\0")
                '\u001a' -> append("\\Z")
                else -> append(char)
            }
        }
        append('\'')
    }
}
