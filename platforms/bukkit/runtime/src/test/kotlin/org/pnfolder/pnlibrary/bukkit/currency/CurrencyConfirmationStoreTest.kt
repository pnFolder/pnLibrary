package org.pnfolder.pnlibrary.bukkit.currency

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture

class CurrencyConfirmationStoreTest {
    private val store = CurrencyConfirmationStore()

    private fun add(timeoutSeconds: Int = 60) = store.add(
        timeoutSeconds = timeoutSeconds,
        currency = "coins",
        description = "give 5",
        requestedBy = "console",
        execute = { CompletableFuture() },
        completed = {},
    )

    @Test
    fun `a stored operation is taken once and its code ignores case`() {
        val operation = add()!!

        assertEquals(10, operation.token.length)
        assertNotNull(store.take(operation.token.lowercase()))
        assertNull(store.take(operation.token))
    }

    @Test
    fun `every operation gets its own code`() {
        val tokens = (1..200).map { add()!!.token }

        assertEquals(200, tokens.toSet().size)
    }

    @Test
    fun `the queue refuses new operations when it is full`() {
        repeat(1_000) { assertNotNull(add()) }

        assertNull(add())
    }

    @Test
    fun `expired operations are discarded and live ones stay`() {
        val expired = add(timeoutSeconds = -1)!!
        val live = add()!!

        store.discardExpired()

        assertNull(store.take(expired.token))
        assertTrue(store.take(live.token) != null)
    }
}
