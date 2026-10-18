package ru.privatenull.pnlibrary.core.downloads

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.downloads.DownloadRegistration
import ru.privatenull.pnlibrary.api.downloads.DownloadSnapshot
import ru.privatenull.pnlibrary.api.downloads.DownloadState
import java.util.concurrent.CompletableFuture

class CompositeDownloadRegistrationTest {
    @Test
    fun `closes both registrations and retains their failures`() {
        val primary = IllegalStateException("first")
        val secondary = IllegalArgumentException("second")
        val first = StubRegistration("first", primary)
        val second = StubRegistration("second", secondary)
        val combined = requireNotNull(CompositeDownloadRegistration.combine(first, second))
        val thrown = assertThrows(IllegalStateException::class.java) { combined.close() }
        assertSame(primary, thrown)
        assertEquals(listOf(secondary), thrown.suppressed.toList())
        assertTrue(first.isClosed)
        assertTrue(second.isClosed)
    }

    @Test
    fun `combines snapshots downloads and close lifecycle`() {
        val first = StubRegistration("first")
        val second = StubRegistration("second")
        val combined = CompositeDownloadRegistration.combine(first, second)

        requireNotNull(combined)
        assertEquals(listOf("first", "second"), combined.snapshots().map { it.key })
        assertEquals(
            listOf("first", "second"),
            combined.downloadNow().toCompletableFuture().join().map { it.key },
        )

        combined.close()
        assertTrue(first.isClosed)
        assertTrue(second.isClosed)
    }

    @Test
    fun `returns the existing registration when there is nothing to combine`() {
        val registration = StubRegistration("only")

        assertSame(registration, CompositeDownloadRegistration.combine(registration, null))
        assertSame(registration, CompositeDownloadRegistration.combine(null, registration))
    }

    private class StubRegistration(private val key: String, private val closeFailure: RuntimeException? = null) : DownloadRegistration {
        override var isClosed: Boolean = false
            private set

        override fun snapshots() = listOf(DownloadSnapshot(key, DownloadState.DECLARED))

        override fun downloadNow() = CompletableFuture.completedFuture(snapshots())

        override fun close() {
            isClosed = true
            closeFailure?.let { throw it }
        }
    }
}
