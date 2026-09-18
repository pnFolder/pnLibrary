package ru.privatenull.pnlibrary.core.upload

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.URI

class UploadProviderChainTest {
    @Test
    fun `uses providers in order until one succeeds`() {
        val calls = mutableListOf<String>()
        val expected = receipt("secondary")
        val chain = UploadProviderChain(
            listOf(
                provider("primary", calls) { throw IOException("offline") },
                provider("secondary", calls) { expected },
            ),
        )

        val result = chain.upload("payload")

        assertSame(expected, result)
        assertEquals(listOf("primary", "secondary"), calls)
    }

    @Test
    fun `routes deletion only to the receipt backend`() {
        var primaryCalled = false
        var secondaryCalled = false
        val primary = deletingProvider("primary") { primaryCalled = true }
        val secondary = deletingProvider("secondary") { secondaryCalled = true }
        val chain = UploadProviderChain(listOf(primary, secondary))

        assertTrue(chain.delete(receipt("secondary")))
        assertFalse(primaryCalled)
        assertTrue(secondaryCalled)
    }

    private fun provider(
        id: String,
        calls: MutableList<String>,
        operation: () -> UploadReceipt,
    ): UploadProvider = object : UploadProvider {
        override val backendId = id
        override fun upload(payload: String): UploadReceipt {
            calls += id
            return operation()
        }
    }

    private fun deletingProvider(id: String, markCalled: () -> Unit): UploadProvider =
        object : UploadProvider {
            override val backendId = id
            override fun upload(payload: String): UploadReceipt = receipt(id)
            override fun delete(receipt: UploadReceipt): Boolean {
                markCalled()
                return true
            }
        }

    private fun receipt(backend: String) = UploadReceipt(
        link = URI.create("https://example.com/report"),
        id = "report",
        deleteToken = "token",
        createdEpochSeconds = 1,
        expiresEpochSeconds = 0,
        backend = backend,
    )
}
