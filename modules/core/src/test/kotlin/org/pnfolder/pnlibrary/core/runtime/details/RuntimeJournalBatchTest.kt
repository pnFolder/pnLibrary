package org.pnfolder.pnlibrary.core.runtime.details

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.logging.LogLevel
import org.pnfolder.pnlibrary.api.runtime.PnLibraryConfig
import org.pnfolder.pnlibrary.spi.platform.PlatformOwnership
import java.nio.file.Files

class RuntimeJournalBatchTest {
    private val ownership = object : PlatformOwnership {
        override fun ownerDetails(owner: Any) = mapOf("id" to "plugin")
    }

    @Test
    fun `a burst is written in fewer blocks than records and nothing disappears without a count`() {
        val folder = Files.createTempDirectory("pnlibrary-journal-batch")
        val journal = RuntimeJournal(folder, PnLibraryConfig(), ownership, 0L, "/pn events")
        val total = 20_000

        repeat(total) { journal.record(this, if (it % 50 == 0) LogLevel.ERROR else LogLevel.INFO, "event $it", null) }
        journal.close()

        val failed = journal.summary()["failedWrites"] as Long
        val stored = journal.store.read(limit = 100_000).size
        assertEquals(total.toLong(), stored + failed)
        assertTrue(journal.store.blocksWritten.get() < total, "blocks: ${journal.store.blocksWritten.get()}")
    }

    @Test
    fun `records recorded one after another are all stored`() {
        val folder = Files.createTempDirectory("pnlibrary-journal-seq")
        val journal = RuntimeJournal(folder, PnLibraryConfig(), ownership, 0L, "/pn events")

        repeat(300) { journal.record(this, LogLevel.WARNING, "w $it", null) }
        journal.close()

        assertEquals((0 until 300).map { "w $it" }.toSet(), journal.store.read(limit = 1_000).map { it.message }.toSet())
        assertEquals(0L, journal.summary()["failedWrites"])
    }
}
