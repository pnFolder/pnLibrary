package org.pnfolder.pnlibrary.api.cooldowns

import org.pnfolder.pnlibrary.api.commands.error.CommandException
import org.pnfolder.pnlibrary.api.commands.error.OnCooldown
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
    /** Whether the caller must wait; the opposite of [allowed]. */
    val isDenied: Boolean get() = !allowed

    /** The wait in whole seconds, rounded up, so a player never reads "0 seconds" while still held back. */
    val remainingSeconds: Long get() = (remaining.toMillis() + 999) / 1000

    /** Returns this result when it is allowed, and throws [OnCooldown] (as a [CommandException]) with the time left when it is denied. */
    fun orThrow(): CooldownResult {
        if (isDenied) throw CommandException(OnCooldown(remaining))
        return this
    }

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
