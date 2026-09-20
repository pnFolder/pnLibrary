package ru.privatenull.pnlibrary.bukkit.updates

import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal enum class UpdateAction { DOWNLOAD, RESTART }

internal class UpdateConfirmationTokens(
    private val random: SecureRandom = SecureRandom(),
    private val clock: () -> Instant = Instant::now,
) {
    private data class Grant(
        val player: UUID,
        val plan: UUID,
        val revision: Long,
        val action: UpdateAction,
        val expiresAt: Instant,
    )

    private val grants = ConcurrentHashMap<String, Grant>()

    fun issue(player: UUID, plan: UUID, revision: Long, action: UpdateAction, lifetime: Duration): String {
        require(!lifetime.isNegative && !lifetime.isZero)
        val bytes = ByteArray(32).also(random::nextBytes)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        grants[token] = Grant(player, plan, revision, action, clock().plus(lifetime))
        return token
    }

    fun consume(token: String, player: UUID, plan: UUID, revision: Long, action: UpdateAction): Boolean {
        val grant = grants[token] ?: return false
        if (grant.player != player || grant.plan != plan || grant.revision != revision || grant.action != action) return false
        if (!clock().isBefore(grant.expiresAt)) {
            grants.remove(token, grant)
            return false
        }
        return grants.remove(token, grant)
    }

    /** Removes expired grants and returns the number still active. */
    fun cleanup(): Int {
        val now = clock()
        grants.entries.removeIf { !now.isBefore(it.value.expiresAt) }
        return grants.size
    }
}
