package org.pnfolder.pnlibrary.core.updates.databases

import org.pnfolder.pnlibrary.spi.libraries.OptionalLibrary
import java.sql.Connection
import java.sql.Driver
import java.util.Properties

/**
 * Opens JDBC connections with a driver that is downloaded on first use.
 * @property libraryId the loader's name for the driver
 * @property driverClass the driver's class, also used to tell whether it is already loaded
 * @property scheme the start of the connection address, such as `jdbc:mariadb://`
 * @property timeouts connect and socket timeouts in the unit the driver wants (MariaDB milliseconds, PostgreSQL seconds)
 */
internal class JdbcConnections(
    private val libraryId: String,
    private val driverClass: String,
    private val scheme: String,
    private val timeouts: Map<String, String>,
    private val library: OptionalLibrary = OptionalLibrary(libraryId, driverClass, JdbcConnections::class.java.classLoader),
) {
    /** Loads the driver if needed and connects; the caller closes the connection. */
    fun open(connection: DatabaseConnection): Connection {
        check(library.ensure()) { "Не удалось загрузить драйвер базы данных: проверьте доступ сервера в интернет и повторите" }
        val properties = Properties().apply {
            setProperty("user", connection.user)
            setProperty("password", connection.password)
            timeouts.forEach { (name, value) -> setProperty(name, value) }
        }
        val url = "$scheme${connection.host}:${connection.port}/${connection.database}"
        val driver = Class.forName(driverClass, true, javaClass.classLoader).getDeclaredConstructor().newInstance() as Driver
        return driver.connect(url, properties) ?: error("Драйвер не принял адрес $url")
    }

    companion object {
        /** MariaDB's driver, which also speaks to MySQL. */
        fun mysql() = JdbcConnections(
            "mysql", "org.mariadb.jdbc.Driver", "jdbc:mariadb://", mapOf("connectTimeout" to "10000", "socketTimeout" to "600000"),
        )

        /** PostgreSQL's driver. */
        fun postgres() = JdbcConnections(
            "postgres", "org.postgresql.Driver", "jdbc:postgresql://", mapOf("connectTimeout" to "10", "socketTimeout" to "600"),
        )
    }
}
