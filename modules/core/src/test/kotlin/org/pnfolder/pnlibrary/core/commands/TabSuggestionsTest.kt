package org.pnfolder.pnlibrary.core.commands

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture

class TabSuggestionsTest {
    @Test
    fun `ready suggestions are returned`() {
        assertEquals(listOf("a"), CompletableFuture.completedFuture(listOf("a")).suggestionsWithin(10))
    }

    @Test
    fun `late suggestions give an empty list`() {
        assertEquals(emptyList<String>(), CompletableFuture<List<String>>().suggestionsWithin(10))
    }

    @Test
    fun `failed suggestions give an empty list`() {
        val failed = CompletableFuture<List<String>>().also { it.completeExceptionally(IllegalStateException("x")) }
        assertEquals(emptyList<String>(), failed.suggestionsWithin(10))
    }
}
