package org.pnfolder.pnlibrary.api.audienceB2.delivery

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.audience.ForwardingAudience
import org.jetbrains.annotations.ApiStatus
import org.pnfolder.pnlibrary.api.audienceB2.name
import org.pnfolder.pnlibrary.common.lang.Failures

/**
 * What became of one attempt to show something (a message, a sound, an effect, a command) to a receiver or a whole
 * audience. Adventure's methods return nothing, so a lost message is invisible; a delivery says who got it and why not.
 *
 * ```kotlin
 * val sent = admins.deliver { it.sendMessage(Component.text("Shop reloaded")) }
 * when (sent.status) {
 *     DeliveryStatus.DELIVERED -> Unit
 *     DeliveryStatus.PARTIAL -> logger.warning("${sent.delivered} of ${sent.receipts.size} admins got it")
 *     DeliveryStatus.UNSUPPORTED -> logger.info("No admin is online")
 *     DeliveryStatus.FAILED -> throw sent.failure!!
 * }
 * ```
 */
@ApiStatus.NonExtendable
interface Delivery {
    /** One answer per receiver, in the order they were asked; empty when the audience had nobody. */
    val receipts: List<Receipt>

    /** The summary of [receipts]. */
    val status: DeliveryStatus

    /** How many receivers got it. */
    val delivered: Int

    /** The first failure reported, or `null`. */
    val failure: Throwable?

    /** Returns this delivery when every receiver got it; otherwise throws, with the first failure as the cause. */
    fun orThrow(): Delivery

    /** Ways to get a delivery. */
    companion object {
        private val DELIVERED = DeliveryReport(listOf(Receipt(null, DeliveryStatus.DELIVERED)))
        private val UNSUPPORTED = DeliveryReport(listOf(Receipt(null, DeliveryStatus.UNSUPPORTED)))

        /** The platform accepted the request. */
        @JvmStatic
        fun delivered(): Delivery = DELIVERED

        /** The platform has no way to do this. */
        @JvmStatic
        fun unsupported(): Delivery = UNSUPPORTED

        /** The platform could do this, but the attempt failed with [cause]. */
        @JvmStatic
        fun failed(cause: Throwable): Delivery = DeliveryReport(listOf(Receipt(null, DeliveryStatus.FAILED, cause)))

        /** Joins several deliveries into one that keeps every receipt. */
        @JvmStatic
        fun combine(deliveries: Iterable<Delivery>): Delivery {
            val all = deliveries.toList()
            return if (all.size == 1) all.single() else DeliveryReport(all.flatMap { it.receipts })
        }

        /**
         * Runs [operation] on [audience], receiver by receiver. A receiver that throws gets a failed receipt and the
         * others still get the operation; fatal errors propagate.
         */
        @JvmStatic
        fun to(audience: Audience, operation: (Audience) -> Unit): Delivery {
            if (audience is ForwardingAudience) return combine(audience.audiences().map { to(it, operation) })
            return try {
                operation(audience)
                DeliveryReport(listOf(Receipt(audience.name, DeliveryStatus.DELIVERED)))
            } catch (error: Throwable) {
                Failures.rethrowIfFatal(error)
                DeliveryReport(listOf(Receipt(audience.name, DeliveryStatus.FAILED, error)))
            }
        }
    }
}
