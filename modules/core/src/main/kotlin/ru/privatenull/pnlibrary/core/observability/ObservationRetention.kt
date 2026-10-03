package ru.privatenull.pnlibrary.core.observability

import ru.privatenull.pnlibrary.api.observability.Observation
import ru.privatenull.pnlibrary.api.observability.ObservationLevel
import java.time.Duration

internal object ObservationRetention {
    fun active(observations: Collection<Observation>, now: Long): List<Observation> =
        observations.filter { observation ->
            observation.level == ObservationLevel.CRITICAL ||
                now - observation.timestamp <= lifetime(observation.level)
        }

    private fun lifetime(level: ObservationLevel): Long = when (level) {
        ObservationLevel.TRACE -> Duration.ofHours(6).toMillis()
        ObservationLevel.INFO -> Duration.ofDays(7).toMillis()
        ObservationLevel.NOTICE,
        ObservationLevel.WARNING -> Duration.ofDays(30).toMillis()
        ObservationLevel.ERROR -> Duration.ofDays(90).toMillis()
        ObservationLevel.CRITICAL -> Long.MAX_VALUE
    }
}
