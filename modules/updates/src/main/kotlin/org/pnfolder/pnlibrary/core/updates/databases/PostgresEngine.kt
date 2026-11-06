package org.pnfolder.pnlibrary.core.updates.databases

import java.nio.file.Path

/** PostgreSQL through JDBC. */
internal class PostgresEngine(private val connections: JdbcConnections = JdbcConnections.postgres()) : DatabaseEngine {
    override fun dump(connection: DatabaseConnection, target: Path) {
        connections.open(connection).use { PostgresDump().write(it, target) }
    }

    override fun restore(connection: DatabaseConnection, source: Path) {
        val restore = SqlScriptRestore(PostgresDump.HEADER)
        connections.open(connection).use { restore.read(it, source) }
    }
}
