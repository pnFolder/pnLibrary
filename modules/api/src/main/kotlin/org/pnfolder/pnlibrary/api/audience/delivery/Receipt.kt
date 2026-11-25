package org.pnfolder.pnlibrary.api.audience.delivery

/**
 * The answer of one receiver.
 *
 * @property receiver its name, or `null` when the platform call did not go to a named receiver
 * @property outcome what happened
 * @property cause the failure reported by the platform, only for [Outcome.FAILED]
 */
data class Receipt(val receiver: String?, val outcome: Outcome, val cause: Throwable? = null)
