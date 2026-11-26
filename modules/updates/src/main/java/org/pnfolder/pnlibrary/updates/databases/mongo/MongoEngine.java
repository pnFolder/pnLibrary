package org.pnfolder.pnlibrary.updates.databases.mongo;

import java.util.Objects;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.file.Path;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.spi.libraries.OptionalLibrary;
import org.pnfolder.pnlibrary.updates.databases.DatabaseConnection;
import org.pnfolder.pnlibrary.updates.databases.DatabaseEngine;

/**
 * MongoDB through its Java driver, which is loaded through Libby the first time it is needed. The driver's classes
 * are touched only by {@link MongoDump} and {@link MongoRestore}, which run after the load.
 */
public final class MongoEngine implements DatabaseEngine {
    /** Class whose presence shows the MongoDB driver is loaded. */
    private static final String PROBE = "com.mongodb.client.MongoClients";

    private final OptionalLibrary library;

    public MongoEngine() {
        this(new OptionalLibrary("mongo", PROBE, MongoEngine.class.getClassLoader()));
    }

    public MongoEngine(@NotNull OptionalLibrary library) {
        this.library = Objects.requireNonNull(library, "library");
    }

    @Override
    public void dump(@NotNull DatabaseConnection connection, @NotNull List<String> tables, @NotNull Path target) throws Exception {
        ensureDriver();
        new MongoDump().write(address(connection), connection.getDatabase(), tables, target);
    }

    @Override
    public void restore(@NotNull DatabaseConnection connection, @NotNull Path source) throws Exception {
        ensureDriver();
        new MongoRestore().read(address(connection), connection.getDatabase(), source);
    }

    private void ensureDriver() {
        if (!library.ensure()) {
            throw new IllegalStateException(
                    "Не удалось загрузить драйвер MongoDB: проверьте доступ сервера в интернет и повторите");
        }
    }

    private static String address(DatabaseConnection connection) {
        if (connection.getUri() != null) {
            return connection.getUri();
        }
        String login = connection.getUser().isEmpty()
                ? "" : encode(connection.getUser()) + ":" + encode(connection.getPassword()) + "@";
        return "mongodb://" + login + connection.getHost() + ":" + connection.getPort()
                + "/?authSource=" + encode(connection.getDatabase());
    }

    private static String encode(String text) {
        try {
            return URLEncoder.encode(text, "UTF-8");
        } catch (UnsupportedEncodingException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
