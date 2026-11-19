package org.pnfolder.pnlibrary.api.cooldowns

import java.time.Duration

/**
 * Result of an atomic cooldown acquisition attempt. Create it with [allowed] or [denied].
 *
 * @property allowed whether the caller acquired a new cooldown
 * @property remaining time left on the existing cooldown when denied, otherwise zero
 */
class CooldownResult private constructor(
    val allowed: Boolean,
    val remaining: Duration,
) {
    /** Renders as `allowed` or `denied: <remaining>`. */
    override fun toString(): String = if (allowed) "allowed" else "denied: $remaining"

    companion object {
        private val ALLOWED = CooldownResult(true, Duration.ZERO)

        /** Returns the shared result meaning no cooldown is active. */
        @JvmStatic
        fun allowed(): CooldownResult = ALLOWED

        /** Returns a denied result with the time left on the active cooldown. */
        @JvmStatic
        fun denied(remaining: Duration): CooldownResult = CooldownResult(false, remaining)
    }
}
