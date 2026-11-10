package org.pnfolder.pnlibrary.core.runtime.details

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.logging.LogLevel
import org.pnfolder.pnlibrary.api.runtime.PnLibraryConfig
import org.pnfolder.pnlibrary.spi.platform.PlatformOwnership
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RuntimeJournalTest {
    private val ownership = object : PlatformOwnership {
        override fun ownerDetails(owner: Any) = mapOf("id" to "plugin")
    }

    @Test
    fun `a failed journal write is reported once instead of being lost`() {
        val folder = Files.createTempDirectory("pnlibrary-journal-test")
        Files.writeString(folder.resolve("journal"), "a file where the journal folder should be")
        val reported = mutableListOf<Throwable>()
        val first = CountDownLatch(1)
        val journal = RuntimeJournal(folder, PnLibraryConfig(), ownership, 0L, "/pn events") {
            reported += it
            first.countDown()
        }

        journal.record(this, LogLevel.ERROR, "one", null)
        assertTrue(first.await(5, TimeUnit.SECONDS))
        journal.record(this, LogLevel.ERROR, "two", null)
        journal.close()

        assertEquals(1, reported.size)
        assertEquals(2L, journal.summary()["failedWrites"])
    }
}
