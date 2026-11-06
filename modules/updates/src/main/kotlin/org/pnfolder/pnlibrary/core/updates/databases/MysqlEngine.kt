package org.pnfolder.pnlibrary.core.updates.databases

import java.nio.file.Path

/** MySQL and MariaDB through JDBC. */
internal class MysqlEngine(private val connections: MysqlConnections = MysqlConnections()) : DatabaseEngine {
    override fun dump(connection: DatabaseConnection, target: Path) {
        connections.open(connection).use { MysqlDump().write(it, target) }
    }

    override fun restore(connection: DatabaseConnection, source: Path) {
        connections.open(connection).use { MysqlRestore().read(it, source) }
    }
}
