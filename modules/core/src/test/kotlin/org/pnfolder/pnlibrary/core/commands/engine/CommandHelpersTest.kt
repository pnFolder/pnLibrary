package org.pnfolder.pnlibrary.core.commands.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture

class CommandHelpersTest {
    @Test
    fun `page count rounds up and is never below one`() {
        assertEquals(1, pageCount(0, 10))
        assertEquals(1, pageCount(10, 10))
        assertEquals(2, pageCount(11, 10))
    }

    @Test
    fun `ready suggestions are returned`() {
        assertEquals(listOf("a"), CompletableFuture.completedFuture(listOf("a")).suggestionsWithin(50))
    }

    @Test
    fun `late or failed suggestions give an empty list`() {
        assertTrue(CompletableFuture<List<String>>().suggestionsWithin(10).isEmpty())
        val failed = CompletableFuture<List<String>>().also { it.completeExceptionally(IllegalStateException("x")) }
        assertTrue(failed.suggestionsWithin(10).isEmpty())
    }
}
