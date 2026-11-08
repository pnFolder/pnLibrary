package org.pnfolder.pnlibrary.core.updates.databases

import java.sql.Connection
import java.sql.ResultSet

/** Reads the shape of the tables of the current schema from the system catalogs and writes it as statements. */
internal class PostgresSchema(private val connection: Connection) {
    /**
 * A table column; [generated] columns are computed by the server and are not copied.
 * @property name column name
 * @property generated true for a stored generated column
 */
    class Column(val name: String, val generated: Boolean)

    /** Names of the base tables of the current schema. */
    fun tables(): List<String> = strings(
        "SELECT table_name FROM information_schema.tables WHERE table_schema = current_schema() AND table_type = 'BASE TABLE' ORDER BY 1",
    )

    /** Sequences that belong to no identity column; identity columns bring their own. [only] keeps those owned by the listed tables. */
    fun sequences(only: List<String>): List<String> = ownedSequences(
        """
        SELECT s.relname, t.relname FROM pg_class s JOIN pg_namespace n ON n.oid = s.relnamespace
        LEFT JOIN pg_depend d ON d.objid = s.oid AND d.deptype = 'a' LEFT JOIN pg_class t ON t.oid = d.refobjid
        WHERE s.relkind = 'S' AND n.nspname = current_schema()
          AND NOT EXISTS (SELECT 1 FROM pg_depend i WHERE i.objid = s.oid AND i.deptype = 'i') ORDER BY 1
        """,
        only,
    )

    /** `setval` statements that give every sequence its current position back. */
    fun sequencePositions(only: List<String>): List<String> {
        val owners = ownedSequences(
            """
            SELECT s.relname, t.relname FROM pg_class s JOIN pg_namespace n ON n.oid = s.relnamespace
            LEFT JOIN pg_depend d ON d.objid = s.oid AND d.deptype IN ('a', 'i') LEFT JOIN pg_class t ON t.oid = d.refobjid
            WHERE s.relkind = 'S' AND n.nspname = current_schema()
            """,
            only,
        ).toSet()
        return connection.createStatement().use { statement ->
            statement.executeQuery(
                "SELECT sequencename, last_value FROM pg_sequences WHERE schemaname = current_schema() AND last_value IS NOT NULL",
            ).use { rows ->
                generateSequence { if (rows.next()) rows.getString(1) to rows.getLong(2) else null }
                    .filter { (name, _) -> name in owners }
                    .map { (name, value) -> "SELECT setval(${PostgresSyntax.literal(PostgresSyntax.identifier(name))}, $value, true)" }.toList()
            }
        }
    }

    private fun ownedSequences(sql: String, only: List<String>): List<String> = connection.createStatement().use { statement ->
        statement.executeQuery(sql).use { rows ->
            generateSequence { if (rows.next()) rows.getString(1) to rows.getString(2) else null }
                .filter { (_, owner) -> owner != null && TableMask.matches(only, owner) || only.isEmpty() }.map { it.first }.toList()
        }
    }

    /** The columns of [table] in order. */
    fun columns(table: String): List<Column> = connection.prepareStatement(
        "SELECT a.attname, a.attgenerated <> '' FROM pg_attribute a WHERE a.attrelid = ?::regclass AND a.attnum > 0 AND NOT a.attisdropped ORDER BY a.attnum",
    ).use { statement ->
        statement.setString(1, PostgresSyntax.identifier(table))
        statement.executeQuery().use { rows -> generateSequence { if (rows.next()) Column(rows.getString(1), rows.getBoolean(2)) else null }.toList() }
    }

    /** `CREATE TABLE` with columns, defaults and the primary key, unique and check constraints; foreign keys come later. */
    fun createTable(table: String): String {
        val regclass = PostgresSyntax.identifier(table)
        val columns = connection.prepareStatement(
            """
            SELECT a.attname, format_type(a.atttypid, a.atttypmod), a.attnotnull, pg_get_expr(d.adbin, d.adrelid), a.attidentity, a.attgenerated
            FROM pg_attribute a LEFT JOIN pg_attrdef d ON d.adrelid = a.attrelid AND d.adnum = a.attnum
            WHERE a.attrelid = ?::regclass AND a.attnum > 0 AND NOT a.attisdropped ORDER BY a.attnum
            """,
        ).use { statement ->
            statement.setString(1, regclass)
            statement.executeQuery().use { rows ->
                generateSequence { if (rows.next()) columnDefinition(rows) else null }.toList()
            }
        }
        val constraints = constraints(regclass, "contype <> 'f'").map { "CONSTRAINT ${PostgresSyntax.identifier(it.first)} ${it.second}" }
        return "CREATE TABLE $regclass (${(columns + constraints).joinToString(", ")})"
    }

    private fun columnDefinition(rows: ResultSet): String {
        val definition = StringBuilder(PostgresSyntax.identifier(rows.getString(1))).append(' ').append(rows.getString(2))
        val default = rows.getString(4)
        when {
            rows.getString(6) == "s" -> definition.append(" GENERATED ALWAYS AS ($default) STORED")
            rows.getString(5) == "a" -> definition.append(" GENERATED ALWAYS AS IDENTITY")
            rows.getString(5) == "d" -> definition.append(" GENERATED BY DEFAULT AS IDENTITY")
            default != null -> definition.append(" DEFAULT ").append(default)
        }
        if (rows.getBoolean(3)) definition.append(" NOT NULL")
        return definition.toString()
    }

    /** `ALTER TABLE` statements that add the foreign keys of [table]; they run after every table exists. */
    fun foreignKeys(table: String): List<String> {
        val regclass = PostgresSyntax.identifier(table)
        return constraints(regclass, "contype = 'f'").map { "ALTER TABLE $regclass ADD CONSTRAINT ${PostgresSyntax.identifier(it.first)} ${it.second}" }
    }

    /** Indexes that are not behind a constraint, as the server writes them. */
    fun indexes(table: String): List<String> = connection.prepareStatement(
        """
        SELECT indexdef FROM pg_indexes WHERE schemaname = current_schema() AND tablename = ?
          AND indexname NOT IN (SELECT conname FROM pg_constraint WHERE conrelid = ?::regclass AND contype IN ('p', 'u', 'x'))
        """,
    ).use { statement ->
        statement.setString(1, table)
        statement.setString(2, PostgresSyntax.identifier(table))
        statement.executeQuery().use { rows -> generateSequence { if (rows.next()) rows.getString(1) else null }.toList() }
    }

    private fun constraints(regclass: String, filter: String): List<Pair<String, String>> = connection.prepareStatement(
        "SELECT conname, pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid = ?::regclass AND $filter ORDER BY conname",
    ).use { statement ->
        statement.setString(1, regclass)
        statement.executeQuery().use { rows -> generateSequence { if (rows.next()) rows.getString(1) to rows.getString(2) else null }.toList() }
    }

    private fun strings(sql: String): List<String> = connection.createStatement().use { statement ->
        statement.executeQuery(sql).use { rows -> generateSequence { if (rows.next()) rows.getString(1) else null }.toList() }
    }
}
