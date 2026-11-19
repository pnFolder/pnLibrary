package org.pnfolder.pnlibrary.core.observability.store

import org.pnfolder.pnlibrary.api.observability.observation.Observation
import org.pnfolder.pnlibrary.api.observability.observation.ObservationKeys
import org.pnfolder.pnlibrary.api.observability.observation.ObservationLevel
import java.time.Duration

/** Hard bound prevents even critical observations from growing the journal forever. */
internal const val MAX_OBSERVATIONS = 10_000

/** The observations still worth keeping at [now]: how long each level lives, critical ones until the hard size bound. */
internal fun Collection<Observation>.retained(now: Long): List<Observation> =
    filter { observation -> observation.isActive(now) }.takeLast(MAX_OBSERVATIONS)

/** The author's period, when set, replaces the level's default in both directions. */
private fun Observation.isActive(now: Long): Boolean {
    val keepUntil = data[ObservationKeys.KEEP_UNTIL_MS]?.toLongOrNull()
    if (keepUntil != null) return now <= keepUntil
    return level == ObservationLevel.CRITICAL || now - timestamp <= level.lifetime()
}

private fun ObservationLevel.lifetime(): Long = when (this) {
    ObservationLevel.TRACE -> Duration.ofHours(6).toMillis()
    ObservationLevel.INFO -> Duration.ofDays(7).toMillis()
    ObservationLevel.NOTICE,
    ObservationLevel.WARNING -> Duration.ofDays(30).toMillis()
    ObservationLevel.ERROR -> Duration.ofDays(90).toMillis()
    ObservationLevel.CRITICAL -> Long.MAX_VALUE
}
