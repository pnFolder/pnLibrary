package ru.privatenull.pnlibrary.api.observability

/**
 * Selects observations from the shared chronological journal.
 *
 * Every non-null property narrows the result. Implementations return the
 * newest matching observations first and never return more than [limit].
 *
 * @property limit maximum number of observations to return
 * @property plugin optional logical plugin name
 * @property minimumLevel optional lowest accepted severity
 * @property since optional inclusive lower timestamp in epoch milliseconds
 * @property until optional inclusive upper timestamp in epoch milliseconds
 */
data class ObservationQuery(
    val limit: Int = 50,
    val plugin: String? = null,
    val minimumLevel: ObservationLevel? = null,
    val since: Long? = null,
    val until: Long? = null,
)
