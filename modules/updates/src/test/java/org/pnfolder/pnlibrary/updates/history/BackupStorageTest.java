package org.pnfolder.pnlibrary.updates.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.updates.backup.BackupKind;
import org.pnfolder.pnlibrary.api.updates.backup.UpdateBackupItem;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;

class BackupStorageTest {
    @TempDir
    Path directory;

    @Test
    void aSetExpiresOnlyAfterThirtyDaysAndNeverWithoutADate() {
        Instant now = Instant.parse("2026-11-06T00:00:00Z");
        assertFalse(BackupStorage.expired(now.minus(Duration.ofDays(29)), now));
        assertTrue(BackupStorage.expired(now.minus(Duration.ofDays(31)), now));
        assertFalse(BackupStorage.expired(null, now));
    }

    @Test
    void deletingAFolderRemovesItsFilesAndCountsTheirSizeFirst() throws IOException {
        Path folder = Files.createDirectories(directory.resolve("set/blobs"));
        Files.write(folder.resolve("a.bin"), new byte[1500]);
        Files.write(directory.resolve("set/manifest.json"), new byte[500]);
        assertEquals(2000, BackupStorage.bytes(directory.resolve("set")));
        BackupStorage.delete(directory.resolve("set"));
        assertFalse(Files.exists(directory.resolve("set")));
        assertEquals(0, BackupStorage.bytes(directory.resolve("set")));
    }

    @Test
    void anItemIsDescribedByItsKindNameAndSize() {
        UpdateBackupItem item = new UpdateBackupItem(ProductId.of("pnclans"), BackupKind.DATABASE, "Кланы",
            3L * 1024 * 1024);
        assertEquals("pnclans: База данных «Кланы» · 3.0 МБ", BackupItemText.describe(item));
        assertEquals("pnclans: Конфигурация · 10 Б", BackupItemText.describe(
            new UpdateBackupItem(ProductId.of("pnclans"), BackupKind.CONFIGURATION, "", 10)));
    }

    @Test
    void childIgnoresLetterCaseAndReturnsNullForAMissingNameOrParent() throws IOException {
        Files.createDirectories(directory.resolve("parent/PnClans"));
        Files.write(directory.resolve("parent/file"), new byte[1]);
        assertEquals("PnClans", BackupStorage.child(directory.resolve("parent"), "pnclans").getFileName().toString());
        assertNull(BackupStorage.child(directory.resolve("parent"), "file"));
        assertNull(BackupStorage.child(directory.resolve("parent"), "other"));
        assertNull(BackupStorage.child(directory.resolve("missing"), "x"));
    }

    @Test
    void copyKeepsTheStructureReplacesExistingFilesAndCopiesEmptyFolders() throws IOException {
        Path from = Files.createDirectories(directory.resolve("from/sub"));
        Files.createDirectories(directory.resolve("from/empty"));
        Files.write(from.resolve("a.txt"), "new".getBytes(StandardCharsets.UTF_8));
        Files.createDirectories(directory.resolve("to"));
        Files.write(directory.resolve("to/a.txt"), "old".getBytes(StandardCharsets.UTF_8));
        Files.write(directory.resolve("from/top.txt"), "t".getBytes(StandardCharsets.UTF_8));
        BackupStorage.copy(directory.resolve("from"), directory.resolve("to"));
        assertEquals("new", text(directory.resolve("to/sub/a.txt")));
        assertEquals("t", text(directory.resolve("to/top.txt")));
        assertTrue(Files.isDirectory(directory.resolve("to/empty")));
        assertEquals("old", text(directory.resolve("to/a.txt")));
    }

    private static String text(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }
}
