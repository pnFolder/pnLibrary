package org.pnfolder.pnlibrary.api.commands.error

import java.time.Duration

/**
 * The sender used the command again too soon.
 *
 * @property remaining how long to wait
 */
open class OnCooldown(val remaining: Duration) : CommandError() {
    override fun placeholders(): Map<String, Any?> =
        mapOf("seconds" to ((remaining.toMillis() + 999) / 1000), "remaining" to remaining)
}
