package org.pnfolder.pnlibrary.api.updates

/** Release maturity accepted by an updater. Each channel includes more stable releases. */
enum class UpdateChannel {
    /** Stable releases only; prereleases, alpha, beta, and release candidates are excluded. */
    STABLE,
    /** Release candidates and stable releases; beta and earlier remain excluded. */
    RC,
    /** Stable and beta releases; alpha releases remain excluded. */
    BETA,
    /** Every release, including experimental alpha versions. */
    ALPHA,
    /** Every published build, including development releases. */
    DEV;

    /** Returns whether this maximum-risk channel permits [candidate]. */
    fun accepts(candidate: UpdateChannel): Boolean = candidate.ordinal <= ordinal
}
