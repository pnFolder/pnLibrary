package org.pnfolder.pnlibrary.api.cooldowns.builder

import org.pnfolder.pnlibrary.api.cooldowns.Cooldown
import net.kyori.adventure.audience.Audience
import java.time.Duration

/**
 * What a [Cooldown.Builder.onDenied] handler is told when someone is held back.
 *
 * @property who the one who has to wait
 * @property action the name of the cooldown
 * @property remaining how long is left
 */
data class CooldownDenied(val who: Audience, val action: String, val remaining: Duration) {
    /** The wait in whole seconds, rounded up, so nobody reads "0 seconds" while still held back. */
    val remainingSeconds: Long get() = (remaining.toMillis() + 999) / 1000
}
