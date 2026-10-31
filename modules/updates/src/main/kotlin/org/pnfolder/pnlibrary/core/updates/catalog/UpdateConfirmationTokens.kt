package org.pnfolder.pnlibrary.core.updates.catalog

import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Describes the operation authorized by a single-use confirmation token. */
enum class UpdateAction {
    /** Restart the server at once (after a short countdown for players) to apply the staged plan. */
    RESTART,

    /** Restart the server after a five-minute countdown shown to the players. */
    RESTART_LATER,

    /** Restart the server as soon as no player is online. */
    RESTART_EMPTY,

    /** Roll back the whole current update plan. */
    PLAN_ROLLBACK,

    /** Restore one saved backup set; the grant is bound to the backup id. */
    ROLLBACK,

    /** Restore one saved backup set together with its configuration files. */
    ROLLBACK_CONFIGS,
}

/**
 * Issues single-use grants bound to a player, update plan, revision, and action.
 *
 * Grants expire according to the supplied clock; mismatched requests cannot consume them.
 */
class UpdateConfirmationTokens(
    private val random: SecureRandom = SecureRandom(),
    private val clock: () -> Instant = Instant::now,
) {
    /** Immutable authorization captured when a token is issued. */
    data class Grant(
        val player: UUID,
        val plan: UUID,
        val revision: Long,
        val action: UpdateAction,
        val expiresAt: Instant,
    )

    private val grants = ConcurrentHashMap<String, Grant>()

    /** Issues an unpredictable token for a strictly positive lifetime. */
    fun issue(
        player: UUID,
        plan: UUID,
        revision: Long,
        action: UpdateAction,
        lifetime: Duration,
    ): String {
        require(!lifetime.isNegative && !lifetime.isZero)
        val bytes = ByteArray(32).also(random::nextBytes)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        grants[token] = Grant(player, plan, revision, action, clock().plus(lifetime))
        return token
    }

    /** Consumes a matching, unexpired grant atomically; a token can succeed only once. */
    fun consume(
        token: String,
        player: UUID,
        plan: UUID,
        revision: Long,
        action: UpdateAction,
    ): Boolean {
        val grant = grants[token] ?: return false
        if (grant.player != player || grant.plan != plan || grant.revision != revision || grant.action != action) return false
        if (!clock().isBefore(grant.expiresAt)) {
            grants.remove(token, grant)
            return false
        }
        return grants.remove(token, grant)
    }

    /**
     * Consumes the grant of [token] for [player] whatever its action, and returns it, or `null` when the token is
     * unknown, expired, already used or was issued to someone else.
     */
    fun redeem(token: String, player: UUID): Grant? {
        val grant = grants[token] ?: return null
        if (grant.player != player) return null
        if (!clock().isBefore(grant.expiresAt)) {
            grants.remove(token, grant)
            return null
        }
        return grant.takeIf { grants.remove(token, it) }
    }

    /** Removes expired grants and returns the number still active. */
    fun cleanup(): Int {
        val now = clock()
        grants.entries.removeIf { !now.isBefore(it.value.expiresAt) }
        return grants.size
    }
}
