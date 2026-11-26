package org.pnfolder.pnlibrary.updates.databases;

import java.util.Objects;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.spi.libraries.OptionalLibrary;

/** Opens JDBC connections with a driver that is downloaded on first use. */
public final class JdbcConnections {
    private final String driverClass;
    private final String scheme;
    private final Map<String, String> timeouts;
    private final OptionalLibrary library;

    /**
     * @param libraryId the loader's name for the driver
     * @param driverClass the driver's class, also used to tell whether it is already loaded
     * @param scheme the start of the connection address, such as {@code jdbc:mariadb://}
     * @param timeouts connect and socket timeouts in the unit the driver wants (MariaDB milliseconds, PostgreSQL seconds)
     */
    public JdbcConnections(@NotNull String libraryId, @NotNull String driverClass, @NotNull String scheme,
                           @NotNull Map<String, String> timeouts) {
        this(driverClass, scheme, timeouts,
                new OptionalLibrary(libraryId, driverClass, JdbcConnections.class.getClassLoader()));
    }

    public JdbcConnections(@NotNull String libraryId, @NotNull String driverClass, @NotNull String scheme,
                           @NotNull Map<String, String> timeouts, @NotNull OptionalLibrary library) {
        this(driverClass, scheme, timeouts, library);
    }

    private JdbcConnections(String driverClass, String scheme, Map<String, String> timeouts, OptionalLibrary library) {
        this.driverClass = Objects.requireNonNull(driverClass, "driverClass");
        this.scheme = Objects.requireNonNull(scheme, "scheme");
        this.timeouts = Objects.requireNonNull(timeouts, "timeouts");
        this.library = library;
    }

    /** MariaDB's driver, which also speaks to MySQL. */
    public static JdbcConnections mysql() {
        return new JdbcConnections("mysql", "org.mariadb.jdbc.Driver", "jdbc:mariadb://",
                timeouts("connectTimeout", "10000", "socketTimeout", "600000"));
    }

    /** PostgreSQL's driver. */
    public static JdbcConnections postgres() {
        return new JdbcConnections("postgres", "org.postgresql.Driver", "jdbc:postgresql://",
                timeouts("connectTimeout", "10", "socketTimeout", "600"));
    }

    /** Loads the driver if needed and connects; the caller closes the connection. */
    public Connection open(@NotNull DatabaseConnection connection) throws SQLException, ReflectiveOperationException {
        if (!library.ensure()) {
            throw new IllegalStateException(
                    "Не удалось загрузить драйвер базы данных: проверьте доступ сервера в интернет и повторите");
        }
        Properties properties = new Properties();
        properties.setProperty("user", connection.getUser());
        properties.setProperty("password", connection.getPassword());
        for (Map.Entry<String, String> timeout : timeouts.entrySet()) {
            properties.setProperty(timeout.getKey(), timeout.getValue());
        }
        String url = scheme + connection.getHost() + ":" + connection.getPort() + "/" + connection.getDatabase();
        Driver driver = (Driver) Class.forName(driverClass, true, getClass().getClassLoader())
                .getDeclaredConstructor().newInstance();
        Connection opened = driver.connect(url, properties);
        if (opened == null) {
            throw new IllegalStateException("Драйвер не принял адрес " + url);
        }
        return opened;
    }

    private static Map<String, String> timeouts(String first, String firstValue, String second, String secondValue) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(first, firstValue);
        map.put(second, secondValue);
        return Collections.unmodifiableMap(map);
    }
}
