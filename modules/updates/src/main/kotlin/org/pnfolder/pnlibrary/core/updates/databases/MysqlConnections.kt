package org.pnfolder.pnlibrary.core.updates.databases

import org.pnfolder.pnlibrary.spi.libraries.OptionalLibrary
import java.sql.Connection
import java.sql.Driver
import java.util.Properties

/** Opens connections with the MariaDB driver, which also speaks to MySQL. The driver is downloaded on first use. */
internal class MysqlConnections(
    private val library: OptionalLibrary = OptionalLibrary("mysql", DRIVER_CLASS, MysqlConnections::class.java.classLoader),
) {
    fun open(connection: DatabaseConnection): Connection {
        check(library.ensure()) { "Не удалось загрузить драйвер базы данных: проверьте доступ сервера в интернет и повторите" }
        val properties = Properties().apply {
            setProperty("user", connection.user)
            setProperty("password", connection.password)
            setProperty("connectTimeout", "10000")
            setProperty("socketTimeout", "600000")
        }
        val url = "jdbc:mariadb://${connection.host}:${connection.port}/${connection.database}"
        val driver = Class.forName(DRIVER_CLASS, true, javaClass.classLoader).getDeclaredConstructor().newInstance() as Driver
        return driver.connect(url, properties) ?: error("Драйвер не принял адрес $url")
    }

    private companion object {
        const val DRIVER_CLASS = "org.mariadb.jdbc.Driver"
    }
}
