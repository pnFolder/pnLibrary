package ru.privatenull.pnlibrary.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JdbcDatabaseTest {
    @TempDir
    Path directory;

    @Test
    void appliesMigrationOnlyOnce() throws Exception {
        JdbcMigration migration = new JdbcMigration(1, "create example",
                List.of("CREATE TABLE example (value VARCHAR(32) NOT NULL)", "INSERT INTO example VALUES ('once')"),
                List.of("CREATE TABLE example (value VARCHAR(32) NOT NULL)", "INSERT INTO example VALUES ('once')"));
        try (JdbcDatabase database = new JdbcDatabase(JdbcSettings.sqlite(directory.resolve("test.db"), 5_000L))) {
            database.open();
            database.migrate("test", List.of(migration));
            database.migrate("test", List.of(migration));
            try (var connection = database.connection();
                 var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT COUNT(*) FROM example")) {
                rows.next();
                assertEquals(1, rows.getInt(1));
            }
        }
    }
}
