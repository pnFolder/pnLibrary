package org.pnfolder.pnlibrary.api.observability.observation

/** Reserved [Observation.data] keys used by the library for retention. */
object ObservationKeys {
    /** Request: how long to keep the observation, in milliseconds; replaced by [KEEP_UNTIL_MS] when stored. */
    const val KEEP_FOR_MS: String = "pn.keepForMs"

    /** Stored: epoch milliseconds after which the observation is dropped. */
    const val KEEP_UNTIL_MS: String = "pn.keepUntilMs"
}
