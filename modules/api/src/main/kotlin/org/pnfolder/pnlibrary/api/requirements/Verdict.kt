package org.pnfolder.pnlibrary.api.requirements

/**
 * The answer of a [Requirement]: met, or not met with a reason.
 *
 * ```kotlin
 * when (val verdict = requirement.test(context)) {
 *     Verdict.Met -> grant()
 *     is Verdict.Unmet -> player.sendMessage(Component.text(verdict.reason))
 * }
 * ```
 */
sealed interface Verdict {
    /** Whether the requirement is satisfied. */
    val isMet: Boolean get() = this === Met

    /** The requirement is satisfied. */
    data object Met : Verdict

    /** The requirement is not satisfied; [reason] is short, readable text. */
    data class Unmet(val reason: String) : Verdict

    /** Helpers for building verdicts. */
    companion object {
        /** [Met] when [condition] holds, otherwise [Unmet] with [reason]. */
        @JvmStatic
        fun of(condition: Boolean, reason: String): Verdict = if (condition) Met else Unmet(reason)
    }
}
