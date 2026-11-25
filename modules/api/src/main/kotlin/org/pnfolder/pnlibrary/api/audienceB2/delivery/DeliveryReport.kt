package org.pnfolder.pnlibrary.api.audienceB2.delivery

internal class DeliveryReport(override val receipts: List<Receipt>) : Delivery {
    override val status: DeliveryStatus = when {
        receipts.isEmpty() -> DeliveryStatus.UNSUPPORTED
        receipts.all { it.status == DeliveryStatus.DELIVERED } -> DeliveryStatus.DELIVERED
        receipts.any { it.status == DeliveryStatus.DELIVERED } -> DeliveryStatus.PARTIAL
        receipts.any { it.status == DeliveryStatus.FAILED } -> DeliveryStatus.FAILED
        else -> DeliveryStatus.UNSUPPORTED
    }

    override val delivered: Int get() = receipts.count { it.status == DeliveryStatus.DELIVERED }

    override val failure: Throwable? get() = receipts.firstNotNullOfOrNull { it.cause }

    override fun orThrow(): Delivery {
        if (status != DeliveryStatus.DELIVERED) {
            throw IllegalStateException("Delivery $status: $delivered of ${receipts.size} receivers got it", failure)
        }
        return this
    }

    override fun equals(other: Any?): Boolean = other is DeliveryReport && other.receipts == receipts

    override fun hashCode(): Int = receipts.hashCode()

    override fun toString(): String = "Delivery($status, $delivered of ${receipts.size})"
}
