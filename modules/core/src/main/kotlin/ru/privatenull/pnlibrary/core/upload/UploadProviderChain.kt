package ru.privatenull.pnlibrary.core.upload

import java.io.IOException
import java.nio.file.Path

/**
 * Tries independent upload providers in deterministic configuration order.
 *
 * A provider failure does not prevent later providers from running. If every
 * provider fails, the thrown [IOException] contains a bounded summary identifying
 * each failed backend. Deletion is routed directly to the backend recorded in the
 * receipt and is never attempted against unrelated providers.
 *
 * @param providers non-empty ordered fallback list
 * @throws IllegalArgumentException if [providers] is empty
 */
class UploadProviderChain(private val providers: List<UploadProvider>) : UploadProvider {
    init { require(providers.isNotEmpty()) { "At least one upload provider is required" } }
    override val backendId = "provider-chain"
    override fun upload(payload: String): UploadReceipt = attempt { it.upload(payload) }
    override fun uploadFile(file: Path, contentType: String): UploadReceipt = attempt { it.uploadFile(file, contentType) }
    override fun delete(receipt: UploadReceipt): Boolean =
        providers.firstOrNull { it.backendId == receipt.backend }?.delete(receipt) ?: false

    private fun attempt(operation: (UploadProvider) -> UploadReceipt): UploadReceipt {
        val failures = mutableListOf<String>()
        providers.forEach { provider ->
            try { return operation(provider) }
            catch (error: Exception) { failures += "${provider.backendId}: ${error.message ?: error.javaClass.simpleName}" }
        }
        throw IOException("Every upload provider failed: ${failures.joinToString("; ")}")
    }
}
