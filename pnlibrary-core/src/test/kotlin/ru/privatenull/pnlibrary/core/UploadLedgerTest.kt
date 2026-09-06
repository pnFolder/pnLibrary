package ru.privatenull.pnlibrary.core

import ru.privatenull.pnlibrary.core.upload.UploadLedger
import ru.privatenull.pnlibrary.core.upload.UploadReceipt


import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URI
import java.nio.file.Path

class UploadLedgerTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `records receipt and survives corrupted json file`() {
        val ledgerFile = tempDir.resolve("upload-ledger.json")
        val ledger = UploadLedger(ledgerFile)

        val receipt = UploadReceipt(
            link = URI.create("https://mclo.gs/test1"),
            id = "test1",
            deleteToken = "token12345678901234567890",
            createdEpochSeconds = System.currentTimeMillis() / 1000,
            expiresEpochSeconds = 0,
            backend = "mclogs"
        )

        ledger.record(receipt, 90)
        assertTrue(ledgerFile.toFile().exists())
        assertTrue(ledgerFile.toFile().readText().contains("test1"))

        // Corrupt file and test recovery
        ledgerFile.toFile().writeText("CORRUPTED INVALID JSON {{{")
        ledger.record(receipt, 90)
        assertTrue(ledgerFile.toFile().readText().contains("test1"))
    }
}
