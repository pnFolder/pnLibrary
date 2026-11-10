package org.pnfolder.pnlibrary.core.updates.history

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pnfolder.pnlibrary.api.updates.ProductId
import org.pnfolder.pnlibrary.api.updates.backup.BackupKind
import org.pnfolder.pnlibrary.api.updates.backup.UpdateBackupItem
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

class BackupStorageTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `a set expires only after thirty days and never without a date`() {
        val now = Instant.parse("2026-11-06T00:00:00Z")
        assertFalse(BackupStorage.expired(now.minus(Duration.ofDays(29)), now))
        assertTrue(BackupStorage.expired(now.minus(Duration.ofDays(31)), now))
        assertFalse(BackupStorage.expired(null, now))
    }

    @Test
    fun `deleting a folder removes its files and counts their size first`() {
        val folder = Files.createDirectories(directory.resolve("set/blobs"))
        Files.write(folder.resolve("a.bin"), ByteArray(1500))
        Files.write(directory.resolve("set/manifest.json"), ByteArray(500))
        assertEquals(2000, BackupStorage.bytes(directory.resolve("set")))
        BackupStorage.delete(directory.resolve("set"))
        assertFalse(Files.exists(directory.resolve("set")))
        assertEquals(0, BackupStorage.bytes(directory.resolve("set")))
    }

    @Test
    fun `an item is described by its kind, name and size`() {
        val item = UpdateBackupItem(ProductId.of("pnclans"), BackupKind.DATABASE, "Кланы", 3L * 1024 * 1024)
        assertEquals("pnclans: База данных «Кланы» · 3.0 МБ", BackupItemText.describe(item))
        assertEquals("pnclans: Конфигурация · 10 Б", BackupItemText.describe(UpdateBackupItem(ProductId.of("pnclans"), BackupKind.CONFIGURATION, "", 10)))
    }

    @Test
    fun `child ignores letter case and returns null for a missing name or parent`() {
        Files.createDirectories(directory.resolve("parent/PnClans"))
        Files.write(directory.resolve("parent/file"), ByteArray(1))
        assertEquals("PnClans", BackupStorage.child(directory.resolve("parent"), "pnclans")?.fileName.toString())
        assertEquals(null, BackupStorage.child(directory.resolve("parent"), "file"))
        assertEquals(null, BackupStorage.child(directory.resolve("parent"), "other"))
        assertEquals(null, BackupStorage.child(directory.resolve("missing"), "x"))
    }

    @Test
    fun `copy keeps the structure, replaces existing files and copies empty folders`() {
        val from = Files.createDirectories(directory.resolve("from/sub"))
        Files.createDirectories(directory.resolve("from/empty"))
        Files.write(from.resolve("a.txt"), "new".toByteArray())
        Files.createDirectories(directory.resolve("to"))
        Files.write(directory.resolve("to/a.txt"), "old".toByteArray())
        Files.write(directory.resolve("from/top.txt"), "t".toByteArray())
        BackupStorage.copy(directory.resolve("from"), directory.resolve("to"))
        assertEquals("new", Files.readString(directory.resolve("to/sub/a.txt")))
        assertEquals("t", Files.readString(directory.resolve("to/top.txt")))
        assertTrue(Files.isDirectory(directory.resolve("to/empty")))
        assertEquals("old", Files.readString(directory.resolve("to/a.txt")))
    }
}
