package org.pnfolder.pnlibrary.api.updates.backup

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.updates.PluginUpdateRequest

class BackupExportTest {
    @Test
    fun `an export needs a plain file name`() {
        listOf("", "a/b.sql", "..", "a\b.sql").forEach { name ->
            assertThrows(IllegalArgumentException::class.java) { BackupExport(BackupKind.DATABASE, "db", name) {} }
        }
    }

    @Test
    fun `file names of exports must differ`() {
        val builder = PluginUpdateRequest.builder().backupExport(BackupKind.DATABASE, "Кланы", "clans.sql") {}
        assertThrows(IllegalArgumentException::class.java) { builder.backupExport(BackupKind.DATABASE, "Ещё", "clans.sql") {} }
        assertEquals(1, builder.exports.size)
    }
}
