package org.pnfolder.pnlibrary.core.updates.databases.mysql

import org.pnfolder.pnlibrary.core.updates.databases.DatabaseConnection
import org.pnfolder.pnlibrary.core.updates.databases.DatabaseEngine
import org.pnfolder.pnlibrary.core.updates.databases.JdbcConnections
import org.pnfolder.pnlibrary.core.updates.databases.SqlScriptRestore
import java.nio.file.Path

/** MySQL and MariaDB through JDBC. */
internal class MysqlEngine(private val connections: JdbcConnections = JdbcConnections.mysql()) : DatabaseEngine {
    override fun dump(connection: DatabaseConnection, tables: List<String>, target: Path) {
        connections.open(connection).use { MysqlDump().write(it, tables, target) }
    }

    override fun restore(connection: DatabaseConnection, source: Path) {
        val restore = SqlScriptRestore(
            MysqlDump.HEADER,
            setup = listOf("SET SESSION sql_mode = REPLACE(@@sql_mode, 'NO_BACKSLASH_ESCAPES', '')", "SET SESSION FOREIGN_KEY_CHECKS = 0"),
            teardown = listOf("SET SESSION FOREIGN_KEY_CHECKS = 1"),
        )
        connections.open(connection).use { restore.read(it, source) }
    }
}
