package org.pnfolder.pnlibrary.api.updates.backup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.updates.request.PluginUpdateRequest;

class BackupExportTest {
    @Test
    void anExportNeedsAPlainFileName() {
        for (String name : new String[] {"", "a/b.sql", "..", "a\\b.sql"}) {
            assertThrows(IllegalArgumentException.class,
                () -> new BackupExport(BackupKind.DATABASE, "db", name, file -> { }));
        }
    }

    @Test
    void fileNamesOfExportsMustDiffer() {
        PluginUpdateRequest.Builder builder = PluginUpdateRequest.builder().repository("pnFolder", "pnClans")
            .backupExport(BackupKind.DATABASE, "Кланы", "clans.sql", file -> { });
        assertThrows(IllegalArgumentException.class,
            () -> builder.backupExport(BackupKind.DATABASE, "Ещё", "clans.sql", file -> { }));
        assertEquals(1, builder.build().getExports().size());
    }
}
