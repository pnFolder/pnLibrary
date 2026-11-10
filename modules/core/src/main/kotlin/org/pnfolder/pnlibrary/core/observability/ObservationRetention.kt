package org.pnfolder.pnlibrary.core.observability

import org.pnfolder.pnlibrary.api.observability.Observation
import org.pnfolder.pnlibrary.api.observability.ObservationKeys
import org.pnfolder.pnlibrary.api.observability.ObservationLevel
import java.time.Duration

/** How long observations of each level are kept; critical ones stay until the hard size bound. */
internal object ObservationRetention {
    fun active(observations: Collection<Observation>, now: Long): List<Observation> =
        observations.filter { observation -> isActive(observation, now) }.takeLast(MAX_OBSERVATIONS)

    /** The author's period, when set, replaces the level's default in both directions. */
    private fun isActive(observation: Observation, now: Long): Boolean {
        val keepUntil = observation.data[ObservationKeys.KEEP_UNTIL_MS]?.toLongOrNull()
        if (keepUntil != null) return now <= keepUntil
        return observation.level == ObservationLevel.CRITICAL ||
            now - observation.timestamp <= lifetime(observation.level)
    }

    /** Hard bound prevents even critical observations from growing the journal forever. */
    internal const val MAX_OBSERVATIONS = 10_000

    private fun lifetime(level: ObservationLevel): Long = when (level) {
        ObservationLevel.TRACE -> Duration.ofHours(6).toMillis()
        ObservationLevel.INFO -> Duration.ofDays(7).toMillis()
        ObservationLevel.NOTICE,
        ObservationLevel.WARNING -> Duration.ofDays(30).toMillis()
        ObservationLevel.ERROR -> Duration.ofDays(90).toMillis()
        ObservationLevel.CRITICAL -> Long.MAX_VALUE
    }
}
