package ru.privatenull.pnlibrary.core.updates.engine

import java.time.Duration

/** Parses and validates administrator-supplied update-freeze durations: from one minute to seven days. */
internal object FreezeDuration {
    /** The longest pause: updates of a component can never be held for more than a week. */
    val MAXIMUM: Duration = Duration.ofDays(7)

    private val minimum = Duration.ofMinutes(1)
    private val maximum = MAXIMUM
    private val pattern = Regex("^(\\d+)([mhd])$", RegexOption.IGNORE_CASE)

    /** Parses a positive `m`, `h`, or `d` duration within supported bounds. */
    fun parse(value: String): Duration {
        val match = pattern.matchEntire(value.trim())
            ?: throw IllegalArgumentException("Freeze duration must use m, h, or d")
        val amount = match.groupValues[1].toLongOrNull()
            ?: throw IllegalArgumentException("Freeze duration is too large")
        val duration = when (match.groupValues[2].lowercase()) {
            "m" -> Duration.ofMinutes(amount)
            "h" -> Duration.ofHours(amount)
            "d" -> Duration.ofDays(amount)
            else -> error("Unsupported duration unit")
        }
        return validate(duration)
    }

    /** Returns [duration] after verifying the supported freeze interval. */
    fun validate(duration: Duration): Duration {
        require(duration >= minimum && duration <= maximum) {
            "Freeze duration must be between 1 minute and 7 days"
        }
        return duration
    }
}
