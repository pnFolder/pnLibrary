package ru.privatenull.pnlibrary.update

import java.time.Duration

/** Parses and validates administrator-supplied update-freeze durations. */
object FreezeDuration {
    private val minimum = Duration.ofMinutes(1)
    private val maximum = Duration.ofDays(30)
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
            "Freeze duration must be between 1 minute and 30 days"
        }
        return duration
    }
}
