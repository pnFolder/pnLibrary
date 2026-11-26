package org.pnfolder.pnlibrary.api.audience.delivery;

/** The summary of a whole {@link Delivery}. */
public enum DeliveryStatus {
    /** Every receiver got it. */
    DELIVERED,

    /** Some receivers got it and others did not. */
    PARTIAL,

    /** Nobody got it and nothing went wrong: the platform has no way to do this, or the audience had nobody. */
    UNSUPPORTED,

    /** Nobody got it and at least one attempt failed; see {@link Delivery#getFailure()}. */
    FAILED
}
