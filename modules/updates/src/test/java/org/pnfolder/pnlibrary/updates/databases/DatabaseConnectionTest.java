package org.pnfolder.pnlibrary.updates.databases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.config.DatabaseKind;
import org.pnfolder.pnlibrary.api.config.DatabaseRole;

class DatabaseConnectionTest {
    @TempDir
    Path directory;

    private DatabaseSource source(String yaml, String path) throws IOException {
        return source(yaml, path, Collections.emptyMap(), "Кланы");
    }

    private DatabaseSource source(String yaml, String path, Map<DatabaseRole, String> keys, String name) throws IOException {
        Path file = directory.resolve("config.yml");
        Files.write(file, yaml.getBytes());
        return new DatabaseSource("pnclans", DatabaseKind.MYSQL, name, file, path, keys);
    }

    @Test
    void usualNamesAreFoundAndThePortDefaultsTo3306() throws Exception {
        DatabaseConnection connection = DatabaseConnection.read(
                source("storage:\n  host: db.local\n  database: clans\n  username: root\n  password: pw\n", "storage"));
        assertEquals("root@db.local:3306/clans", connection.toString());
        assertEquals("pw", connection.getPassword());
    }

    @Test
    void fieldsWithOtherNamesAreReadThroughTheirRoles() throws Exception {
        Map<DatabaseRole, String> keys = new EnumMap<>(DatabaseRole.class);
        keys.put(DatabaseRole.HOST, "server-address");
        keys.put(DatabaseRole.PORT, "tcp");
        keys.put(DatabaseRole.DATABASE, "schema");
        keys.put(DatabaseRole.USER, "login");
        keys.put(DatabaseRole.PASSWORD, "secret");
        String yaml = "mysql:\n  server-address: 10.0.0.5\n  tcp: 3307\n  schema: s\n  login: u\n  secret: p\n";
        assertEquals("u@10.0.0.5:3307/s", DatabaseConnection.read(source(yaml, "mysql", keys, "Кланы")).toString());
    }

    @Test
    void aRootLevelConnectionHasAnEmptyPath() throws Exception {
        assertEquals("u@h:3306/d", DatabaseConnection.read(source("host: h\ndatabase: d\nuser: u\n", "")).toString());
    }

    @Test
    void twoDatabasesOfOneFileDoNotGetMixedUp() throws Exception {
        String yaml = "clans:\n  host: a\n  database: one\n  user: u\nbank:\n  host: b\n  database: two\n  user: u\n";
        Map<DatabaseRole, String> none = Collections.emptyMap();
        assertEquals("u@a:3306/one", DatabaseConnection.read(source(yaml, "clans", none, "Кланы")).toString());
        assertEquals("u@b:3306/two", DatabaseConnection.read(source(yaml, "bank", none, "Банк")).toString());
    }

    @Test
    void aMissingPartIsReportedWithTheDatabaseName() throws Exception {
        DatabaseSource source = source("storage:\n  host: h\n", "storage");
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> DatabaseConnection.read(source));
        assertEquals(true, error.getMessage().contains("Кланы"));
    }

    @Test
    void aMongoConnectionMayBeASingleConnectionStringOrPartsWithoutAUser() throws Exception {
        assertEquals("uri/clans", DatabaseConnection.read(mongo("mongo:\n  uri: mongodb://u:p@h/clans\n  database: clans\n")).toString());
        assertEquals("@h:27017/clans", DatabaseConnection.read(mongo("mongo:\n  host: h\n  database: clans\n")).toString());
    }

    private DatabaseSource mongo(String yaml) throws IOException {
        DatabaseSource it = source(yaml, "mongo");
        return new DatabaseSource(it.getPlugin(), DatabaseKind.MONGODB, it.getName(), it.getFile(), it.getPath(), it.getKeys());
    }

    @Test
    void onlyTheListedTablesAndTheTablesNamedInMarkedFieldsAreCopied() throws Exception {
        String yaml = "storage:\n  host: h\n  database: d\n  user: u\n  members: clan_members\n";
        DatabaseSource base = source(yaml, "storage");
        DatabaseSource marked = new DatabaseSource(base.getPlugin(), base.getKind(), base.getName(), base.getFile(),
                base.getPath(), base.getKeys(), Arrays.asList("clans"), Arrays.asList("members"));
        assertEquals(Arrays.asList("clans", "clan_members"), DatabaseConnection.read(marked).getTables());
        assertEquals(Collections.emptyList(), DatabaseConnection.read(base).getTables());
    }

    @Test
    void anEmptyMarkedTableFieldIsAnErrorAndNotACopyOfEverything() throws Exception {
        DatabaseSource base = source("storage:\n  host: h\n  database: d\n  user: u\n  table: ''\n", "storage");
        DatabaseSource marked = new DatabaseSource(base.getPlugin(), base.getKind(), base.getName(), base.getFile(),
                base.getPath(), base.getKeys(), Collections.emptyList(), Arrays.asList("table"));
        assertThrows(IllegalStateException.class, () -> DatabaseConnection.read(marked));
    }
}
