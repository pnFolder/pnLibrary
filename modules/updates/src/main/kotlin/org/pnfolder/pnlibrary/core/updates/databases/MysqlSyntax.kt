package org.pnfolder.pnlibrary.core.updates.databases

/** Quoting for the statements a copy of a MySQL database is made of. */
internal object MysqlSyntax {
    /** [name] quoted as a MySQL identifier. */
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

    /**
     * [sql] on one line. A line break inside a quoted string (a DEFAULT or COMMENT) becomes `\n`, which keeps the text;
     * elsewhere it is only white space and becomes a space.
     */
    fun singleLine(sql: String): String = buildString(sql.length) {
        var quote: Char? = null
        var index = 0
        while (index < sql.length) {
            val char = sql[index++]
            val inText = quote == '\'' || quote == '"'
            when {
                char == '\n' && inText -> append("\\n")
                char == '\r' && inText -> append("\\r")
                char == '\n' || char == '\r' -> append(' ')
                inText && char == '\\' && index < sql.length -> append(char).append(sql[index++])
                quote == null && (char == '\'' || char == '"' || char == '`') -> { quote = char; append(char) }
                char == quote -> { quote = null; append(char) }
                else -> append(char)
            }
        }
    }
}
