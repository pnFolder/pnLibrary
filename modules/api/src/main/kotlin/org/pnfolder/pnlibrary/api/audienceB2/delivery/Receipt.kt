package org.pnfolder.pnlibrary.api.audienceB2.delivery

/**
 * The answer of one receiver.
 *
 * @property receiver its name, or `null` when the call did not go to a named receiver
 * @property status [DeliveryStatus.DELIVERED], [DeliveryStatus.UNSUPPORTED] or [DeliveryStatus.FAILED]
 * @property cause the failure, only for [DeliveryStatus.FAILED]
 */
data class Receipt(val receiver: String?, val status: DeliveryStatus, val cause: Throwable? = null)
