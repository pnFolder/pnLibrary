package org.pnfolder.pnlibrary.updates.databases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.config.DatabaseKind;

class DatabaseBackupsTest {
    @TempDir
    Path directory;

    private final List<Path> restored = new ArrayList<>();
    private final DatabaseEngine engine = new DatabaseEngine() {
        @Override
        public void dump(@NotNull DatabaseConnection connection, @NotNull List<String> tables, @NotNull Path target)
                throws IOException {
            Files.write(target, "dump".getBytes());
        }

        @Override
        public void restore(@NotNull DatabaseConnection connection, @NotNull Path source) {
            restored.add(source);
        }
    };

    private DatabaseBackups backups() {
        Map<DatabaseKind, DatabaseEngine> engines = new EnumMap<>(DatabaseKind.class);
        engines.put(DatabaseKind.MYSQL, engine);
        return new DatabaseBackups(directory.resolve("databases"), new DatabaseSources(), engines);
    }

    @Test
    void restoreWithoutASavedCopyFailsAndTouchesNothing() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> backups().restore(UUID.randomUUID(), "pnclans", "Кланы"));
        assertTrue(error.getMessage().contains("нет сохранённой базы"));
        assertTrue(restored.isEmpty());
    }

    @Test
    void restoreOfACopyWhosePluginNoLongerDescribesTheDatabaseFailsBeforeRestoring() throws Exception {
        UUID plan = UUID.randomUUID();
        Path saved = directory.resolve("databases/" + plan + "/pnclans");
        Files.createDirectories(saved);
        Files.write(saved.resolve("%D0%9A.db.gz"), new byte[3]);
        assertThrows(IllegalStateException.class, () -> backups().restore(plan, "pnclans", "К"));
        assertTrue(restored.isEmpty());
    }

    @Test
    void itemsOfAPlanWithoutCopiesIsEmpty() throws Exception {
        assertEquals(Collections.emptyList(), backups().items(UUID.randomUUID()));
    }

    @Test
    void copyingNothingCreatesNothing() throws Exception {
        UUID plan = UUID.randomUUID();
        backups().copyBefore(plan, Arrays.asList("pnclans"));
        assertTrue(!Files.exists(directory.resolve("databases/" + plan)));
    }
}
