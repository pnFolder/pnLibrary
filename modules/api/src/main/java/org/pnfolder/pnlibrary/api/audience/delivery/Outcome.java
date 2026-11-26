package org.pnfolder.pnlibrary.api.audience.delivery;

/** What happened to one receiver of a {@link Delivery}. */
public enum Outcome {
    /** The receiver got it. */
    DELIVERED,

    /** The platform has no way to do this. */
    UNSUPPORTED,

    /** The platform could do this, but the attempt failed. */
    FAILED
}
