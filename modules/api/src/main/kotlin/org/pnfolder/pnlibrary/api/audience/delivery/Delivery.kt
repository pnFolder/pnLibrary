package org.pnfolder.pnlibrary.api.audience.delivery

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.audience.ForwardingAudience
import org.pnfolder.pnlibrary.api.audience.name
import org.pnfolder.pnlibrary.common.lang.Failures

/**
 * What became of one attempt to present something (a message, a sound, an effect, a command) to one receiver or a
 * whole audience. Adventure's own methods return nothing, so a lost message is invisible; a [Delivery] says who got
 * it, who could not, and why.
 *
 * ```kotlin
 * val sent = library.audiences.filterAudience { it.hasPermission("shop.admin") }.deliver { it.sendMessage(Component.text("Shop reloaded")) }
 * when (sent.status) {
 *     Delivery.DeliveryStatus.DELIVERED -> Unit
 *     Delivery.DeliveryStatus.PARTIAL -> logger.warning("Only ${sent.delivered} of ${sent.receipts.size} admins got it")
 *     Delivery.DeliveryStatus.UNSUPPORTED -> logger.info("No admin is online")
 *     Delivery.DeliveryStatus.FAILED -> throw sent.failure!!
 * }
 * ```
 *
 * A group is asked receiver by receiver: each one gets the operation even when another fails, and every answer is kept
 * in [receipts]. Players answer for their own effects, particles and commands through the same type.
 *
 * @property receipts one entry per receiver, in the order they were asked; empty when the audience had nobody
 */
class Delivery private constructor(val receipts: List<Receipt>) {
    /** The summary of [receipts]. */
    val status: DeliveryStatus = when {
        receipts.isEmpty() -> DeliveryStatus.UNSUPPORTED
        receipts.all { it.outcome == Outcome.DELIVERED } -> DeliveryStatus.DELIVERED
        receipts.any { it.outcome == Outcome.DELIVERED } -> DeliveryStatus.PARTIAL
        receipts.any { it.outcome == Outcome.FAILED } -> DeliveryStatus.FAILED
        else -> DeliveryStatus.UNSUPPORTED
    }

    /** `true` only when every receiver got it, which is [DeliveryStatus.DELIVERED]. */
    val isDelivered: Boolean get() = status == DeliveryStatus.DELIVERED

    /** How many receivers got it. */
    val delivered: Int get() = receipts.count { it.outcome == Outcome.DELIVERED }

    /** Every failure the platform reported, in order. */
    val failures: List<Throwable> get() = receipts.mapNotNull { it.cause }

    /** The first failure the platform reported, or `null` when there was none. */
    val failure: Throwable? get() = failures.firstOrNull()

    /** Returns this delivery when every receiver got it, and throws otherwise: the first failure is the cause. */
    fun orThrow(): Delivery {
        if (!isDelivered) throw IllegalStateException("Delivery $status: $delivered of ${receipts.size} receivers got it", failure)
        return this
    }

    override fun equals(other: Any?): Boolean = other is Delivery && other.receipts == receipts

    override fun hashCode(): Int = receipts.hashCode()

    override fun toString(): String = "Delivery($status, $delivered of ${receipts.size})"

    /** Ways to get a [Delivery]. */
    companion object {
        private val DELIVERED = Delivery(listOf(Receipt(null, Outcome.DELIVERED)))
        private val UNSUPPORTED = Delivery(listOf(Receipt(null, Outcome.UNSUPPORTED)))

        /** The platform accepted the request. */
        @JvmStatic
        fun delivered(): Delivery = DELIVERED

        /** The platform has no way to do this. */
        @JvmStatic
        fun unsupported(): Delivery = UNSUPPORTED

        /** The platform could do this, but the attempt failed with [cause]. */
        @JvmStatic
        fun failed(cause: Throwable): Delivery = Delivery(listOf(Receipt(null, Outcome.FAILED, cause)))

        /** Maps a platform answer that only says accepted or not to [delivered] or [unsupported]. */
        @JvmStatic
        fun of(accepted: Boolean): Delivery = if (accepted) DELIVERED else UNSUPPORTED

        /** Joins several deliveries into one that keeps every receipt. */
        @JvmStatic
        fun combine(deliveries: Iterable<Delivery>): Delivery {
            val all = deliveries.toList()
            return if (all.size == 1) all.single() else Delivery(all.flatMap { it.receipts })
        }

        /**
         * Runs [operation] on [audience] and reports it. A group is asked receiver by receiver; an error thrown
         * by one receiver becomes its [Outcome.FAILED] receipt, except fatal errors, which propagate.
         */
        @JvmStatic
        fun to(audience: Audience, operation: (Audience) -> Unit): Delivery {
            if (audience is ForwardingAudience) return combine(audience.audiences().map { to(it, operation) })
            return try {
                operation(audience)
                Delivery(listOf(Receipt(audience.name, Outcome.DELIVERED)))
            } catch (error: Throwable) {
                Failures.rethrowIfFatal(error)
                Delivery(listOf(Receipt(audience.name, Outcome.FAILED, error)))
            }
        }
    }
}
