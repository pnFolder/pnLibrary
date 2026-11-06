package org.pnfolder.pnlibrary.core.updates.databases

import java.sql.Connection
import java.sql.Driver
import java.util.Properties

/** Opens connections with the MariaDB driver, which also speaks to MySQL. The driver is loaded through Libby on first use. */
internal class MysqlConnections(private val loadDriver: () -> Boolean = ::loadThroughLoader) {
    fun open(connection: DatabaseConnection): Connection {
        val driver = driver()
        val properties = Properties().apply {
            setProperty("user", connection.user)
            setProperty("password", connection.password)
            setProperty("connectTimeout", "10000")
            setProperty("socketTimeout", "600000")
        }
        val url = "jdbc:mariadb://${connection.host}:${connection.port}/${connection.database}"
        return driver.connect(url, properties) ?: error("Драйвер не принял адрес $url")
    }

    private fun driver(): Driver {
        findDriver()?.let { return it }
        check(loadDriver()) { "Не удалось загрузить драйвер базы данных: проверьте доступ сервера в интернет и повторите" }
        return checkNotNull(findDriver()) { "Драйвер базы данных загружен, но не найден" }
    }

    private fun findDriver(): Driver? = try {
        Class.forName(DRIVER_CLASS, true, javaClass.classLoader).getDeclaredConstructor().newInstance() as Driver
    } catch (_: ClassNotFoundException) {
        null
    }

    private companion object {
        const val DRIVER_CLASS = "org.mariadb.jdbc.Driver"

        // The loader module is plain Java in the same JAR and runs before this code; it cannot be a dependency of this module.
        fun loadThroughLoader(): Boolean = try {
            Class.forName("org.pnfolder.pnlibrary.loader.LibraryLoader")
                .getMethod("loadMysqlDriver").invoke(null) as Boolean
        } catch (_: ReflectiveOperationException) {
            false
        }
    }
}
