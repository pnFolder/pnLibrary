package org.pnfolder.pnlibrary.bukkit.currency

import org.pnfolder.pnlibrary.api.currency.CurrencyResult
import java.security.SecureRandom
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap

/** Console-confirmation queue holding currency operations until an administrator approves them. */
internal class CurrencyConfirmationStore {
    private val pending = ConcurrentHashMap<String, PendingOperation>()
    private val random = SecureRandom()

    /** Stores an operation and returns its confirmation code, or null when the queue is full. */
    fun add(
        timeoutSeconds: Int,
        currency: String,
        description: String,
        requestedBy: String,
        execute: () -> CompletionStage<CurrencyResult>,
        completed: (CurrencyResult) -> Unit,
    ): PendingOperation? {
        if (pending.size >= MAX_PENDING) return null
        val token = newToken()
        val expiresAt = System.currentTimeMillis() + timeoutSeconds * 1_000L
        return PendingOperation(token, expiresAt, currency, description, requestedBy, execute, completed)
            .also { pending[token] = it }
    }

    /** Removes and returns the operation for [token], or null when unknown. */
    fun take(token: String): PendingOperation? = pending.remove(token.uppercase())

    fun discardExpired() {
        val now = System.currentTimeMillis()
        pending.entries.removeIf { it.value.expiresAt < now }
    }

    private fun newToken(): String {
        repeat(32) {
            val value = buildString(TOKEN_LENGTH) { repeat(TOKEN_LENGTH) { append(TOKEN_ALPHABET[random.nextInt(TOKEN_ALPHABET.length)]) } }
            if (!pending.containsKey(value)) return value
        }
        error("Unable to allocate a currency confirmation code")
    }

    internal data class PendingOperation(
        val token: String,
        val expiresAt: Long,
        val currency: String,
        val description: String,
        val requestedBy: String,
        val execute: () -> CompletionStage<CurrencyResult>,
        val completed: (CurrencyResult) -> Unit,
    )

    private companion object {
        const val MAX_PENDING = 1_000
        const val TOKEN_LENGTH = 10
        const val TOKEN_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    }
}
