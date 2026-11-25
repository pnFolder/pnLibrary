package org.pnfolder.pnlibrary.api.scenario.actions.builtin

import org.pnfolder.pnlibrary.api.config.ConfigKey
import org.pnfolder.pnlibrary.api.scenario.actions.Action

/**
 * One reaction of an [AttemptAction] to a kind of failure.
 *
 * A code matches exactly (`currency.insufficient-funds`), as a family (`currency.*`), or everything (`*`).
 *
 * @property codes failure codes this handler takes; empty takes every failure
 * @property steps actions run for it, written under `do`
 */
data class ErrorHandler(
    val codes: List<String> = emptyList(),
    @field:ConfigKey("do")
    val steps: List<Action> = emptyList(),
) {
    /** Whether this handler takes a failure with [code]. */
    fun matches(code: String): Boolean = codes.isEmpty() || codes.any { pattern ->
        pattern == "*" || pattern == code || (pattern.endsWith(".*") && code.startsWith(pattern.dropLast(1)))
    }
}
