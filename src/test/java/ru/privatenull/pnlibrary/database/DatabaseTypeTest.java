package ru.privatenull.pnlibrary.database;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatabaseTypeTest {
    @Test
    void acceptsAliasesAndCase() {
        assertEquals(DatabaseType.SQLITE, DatabaseType.parse(null));
        assertEquals(DatabaseType.MYSQL, DatabaseType.parse("mysql"));
        assertEquals(DatabaseType.MONGODB, DatabaseType.parse("mongo"));
        assertEquals(DatabaseType.REDIS, DatabaseType.parse("redis"));
    }

    @Test
    void rejectsUnknownDatabase() {
        assertThrows(IllegalArgumentException.class, () -> DatabaseType.parse("postgres"));
    }
}
