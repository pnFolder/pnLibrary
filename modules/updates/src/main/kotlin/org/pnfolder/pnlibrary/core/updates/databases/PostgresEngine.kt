package org.pnfolder.pnlibrary.core.updates.databases

import java.nio.file.Path

/** PostgreSQL through JDBC. */
internal class PostgresEngine(private val connections: JdbcConnections = JdbcConnections.postgres()) : DatabaseEngine {
    override fun dump(connection: DatabaseConnection, tables: List<String>, target: Path) {
        connections.open(connection).use { PostgresDump().write(it, tables, target) }
    }

    override fun restore(connection: DatabaseConnection, source: Path) {
        val restore = SqlScriptRestore(PostgresDump.HEADER, atomic = true)
        connections.open(connection).use { restore.read(it, source) }
    }
}
