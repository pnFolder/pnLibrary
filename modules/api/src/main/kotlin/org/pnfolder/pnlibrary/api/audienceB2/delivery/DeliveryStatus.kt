package org.pnfolder.pnlibrary.api.audienceB2.delivery

/** What became of a [Delivery], or of one [Receipt] in it (a receipt is never [PARTIAL]). */
enum class DeliveryStatus {
    /** Every receiver got it. */
    DELIVERED,

    /** Some receivers got it and others did not. */
    PARTIAL,

    /** Nobody got it and nothing went wrong: the platform cannot do this, or the audience had nobody. */
    UNSUPPORTED,

    /** Nobody got it and at least one attempt failed. */
    FAILED,
}
