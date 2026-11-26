package org.pnfolder.pnlibrary.core.upload;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UploadLedgerTest {
    @TempDir
    Path tempDir;

    @Test
    void recordsReceiptAndSurvivesCorruptedJsonFile() throws Exception {
        Path ledgerFile = tempDir.resolve("upload-ledger.json");
        UploadLedger ledger = new UploadLedger(ledgerFile);

        UploadReceipt receipt = new UploadReceipt(URI.create("https://mclo.gs/test1"), "test1",
            "token12345678901234567890", System.currentTimeMillis() / 1000, 0, "mclogs");

        ledger.record(receipt, 90);
        assertTrue(Files.exists(ledgerFile));
        assertTrue(new String(Files.readAllBytes(ledgerFile), StandardCharsets.UTF_8).contains("test1"));

        // Corrupt file and test recovery
        Files.write(ledgerFile, "CORRUPTED INVALID JSON {{{".getBytes(StandardCharsets.UTF_8));
        ledger.record(receipt, 90);
        assertTrue(new String(Files.readAllBytes(ledgerFile), StandardCharsets.UTF_8).contains("test1"));
    }
}
