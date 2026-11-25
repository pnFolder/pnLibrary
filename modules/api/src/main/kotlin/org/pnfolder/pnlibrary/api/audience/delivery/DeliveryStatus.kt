package org.pnfolder.pnlibrary.api.audience.delivery

/** The summary of a whole [Delivery]. */
enum class DeliveryStatus {
    /** Every receiver got it. */
    DELIVERED,

    /** Some receivers got it and others did not. */
    PARTIAL,

    /** Nobody got it and nothing went wrong: the platform has no way to do this, or the audience had nobody. */
    UNSUPPORTED,

    /** Nobody got it and at least one attempt failed; see [Delivery.failure]. */
    FAILED,
}
