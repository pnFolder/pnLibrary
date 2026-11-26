package org.pnfolder.pnlibrary.updates.databases;

import java.util.Objects;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.config.DatabaseKind;
import org.pnfolder.pnlibrary.api.config.DatabaseRole;
import org.pnfolder.pnlibrary.common.io.TextFiles;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** How to reach a database, read from the plugin's own configuration file at the moment it is needed. */
public final class DatabaseConnection {
    /** Names tried for a field that carries no {@code @DatabaseField}. */
    private static final Map<DatabaseRole, List<String>> USUAL = usual();

    private final String host;
    private final int port;
    private final String database;
    private final String user;
    private final String password;
    private final String uri;
    private final List<String> tables;

    public DatabaseConnection(@NotNull String host, int port, @NotNull String database, @NotNull String user,
                              @NotNull String password) {
        this(host, port, database, user, password, null, Collections.emptyList());
    }

    /**
     * @param host server address
     * @param port server port
     * @param database database name
     * @param user login
     * @param password password, never printed
     * @param uri a full connection string, when the plugin keeps one; it replaces host, port, user and password
     * @param tables the tables or collections to copy, empty for all
     */
    public DatabaseConnection(@NotNull String host, int port, @NotNull String database, @NotNull String user,
                              @NotNull String password, @Nullable String uri, @NotNull List<String> tables) {
        this.host = Objects.requireNonNull(host, "host");
        this.port = port;
        this.database = Objects.requireNonNull(database, "database");
        this.user = Objects.requireNonNull(user, "user");
        this.password = Objects.requireNonNull(password, "password");
        this.uri = uri;
        this.tables = tables;
    }

    @NotNull
    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    @NotNull
    public String getDatabase() {
        return database;
    }

    @NotNull
    public String getUser() {
        return user;
    }

    @NotNull
    public String getPassword() {
        return password;
    }

    @Nullable
    public String getUri() {
        return uri;
    }

    @NotNull
    public List<String> getTables() {
        return tables;
    }

    /** The password is left out so a connection never reaches a log by accident. */
    @Override
    public String toString() {
        return uri != null ? "uri/" + database : user + "@" + host + ":" + port + "/" + database;
    }

    /** Reads the connection of {@code source} from its configuration file, applying the usual defaults for the port. */
    @NotNull
    public static DatabaseConnection read(@NotNull DatabaseSource source) throws IOException {
        if (!Files.isRegularFile(source.getFile())) {
            throw new IllegalArgumentException("Файл настроек " + source.getFile().getFileName() + " не найден");
        }
        Map<?, ?> section = section(source);
        boolean mongo = source.getKind() == DatabaseKind.MONGODB;
        String uri = mongo ? text(source, section, DatabaseRole.URI) : null;
        String database = required(text(source, section, DatabaseRole.DATABASE), source, "database");
        List<String> tables = new ArrayList<>(source.getTables());
        for (String key : source.getTableKeys()) {
            String table = value(section, key);
            if (table != null) {
                tables.add(table);
            }
        }
        if (tables.isEmpty() && !source.getTableKeys().isEmpty()) {
            throw new IllegalStateException("В поле @BackupTable базы «" + source.getName() + "» нет имени таблицы");
        }
        if (uri != null) {
            return new DatabaseConnection("", 0, database, "", "", uri, tables);
        }
        String host = required(text(source, section, DatabaseRole.HOST), source, "host");
        int port = port(source, text(source, section, DatabaseRole.PORT));
        String user = text(source, section, DatabaseRole.USER);
        if (user == null) {
            user = mongo ? "" : required(null, source, "user");
        }
        String password = text(source, section, DatabaseRole.PASSWORD);
        return new DatabaseConnection(host, port, database, user, password != null ? password : "", null, tables);
    }

    private static Map<?, ?> section(DatabaseSource source) throws IOException {
        Object node = new Yaml(new SafeConstructor(new LoaderOptions()))
                .load(TextFiles.read(source.getFile(), StandardCharsets.UTF_8));
        for (String part : source.getPath().split("\\.", -1)) {
            if (!part.isEmpty()) {
                node = node instanceof Map ? ((Map<?, ?>) node).get(part) : null;
            }
        }
        if (!(node instanceof Map)) {
            throw new IllegalStateException("В " + source.getFile().getFileName() + " нет раздела «" + source.getPath()
                    + "» с подключением к базе");
        }
        return (Map<?, ?>) node;
    }

    private static String text(DatabaseSource source, Map<?, ?> section, DatabaseRole role) {
        String custom = source.getKeys().get(role);
        List<String> keys = custom != null ? Collections.singletonList(custom) : USUAL.get(role);
        for (String key : keys) {
            String found = value(section, key);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static String value(Map<?, ?> section, String key) {
        Object found = section.get(key);
        if (found == null) {
            return null;
        }
        String text = found.toString();
        return Blank.is(text) ? null : text;
    }

    private static int port(DatabaseSource source, String text) {
        if (text != null) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                // a port that is not a number falls back to the default of the engine
            }
        }
        switch (source.getKind()) {
            case MONGODB:
                return 27017;
            case POSTGRESQL:
                return 5432;
            default:
                return 3306;
        }
    }

    private static String required(String value, DatabaseSource source, String part) {
        if (value == null) {
            throw new IllegalStateException(
                    "Не нашёл «" + part + "» для базы «" + source.getName() + "»: назовите поле так или пометьте его @DatabaseField");
        }
        return value;
    }

    private static Map<DatabaseRole, List<String>> usual() {
        Map<DatabaseRole, List<String>> usual = new EnumMap<>(DatabaseRole.class);
        usual.put(DatabaseRole.HOST, Arrays.asList("host", "hostname", "address"));
        usual.put(DatabaseRole.PORT, Arrays.asList("port"));
        usual.put(DatabaseRole.DATABASE, Arrays.asList("database", "db", "name"));
        usual.put(DatabaseRole.USER, Arrays.asList("user", "username"));
        usual.put(DatabaseRole.PASSWORD, Arrays.asList("password", "pass"));
        usual.put(DatabaseRole.URI, Arrays.asList("uri", "url", "connection-string"));
        return usual;
    }
}
