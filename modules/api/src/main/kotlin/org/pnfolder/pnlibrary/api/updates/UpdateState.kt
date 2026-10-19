package org.pnfolder.pnlibrary.api.updates

/** Current observable phase of an update registration. */
enum class UpdateState {
    /** The installed component is the newest allowed compatible version. */
    UP_TO_DATE,
    /** A newer allowed compatible component version exists. */
    UPDATE_AVAILABLE,
    /** A verified update transaction is staged for restart. */
    UPDATE_STAGED,
    /** Updates for this component are temporarily frozen. */
    FROZEN,
    /** A release exists but cannot run with the selected API or dependencies. */
    INCOMPATIBLE,
    /** A related component prevents a valid atomic update plan. */
    BLOCKED,
    /** A remote release check is currently running. */
    CHECKING,
    /** A complete plan is currently being downloaded and verified. */
    DOWNLOADING,
    /** The latest check or download failed; details are available in the snapshot message. */
    FAILED,
    /** The administrator restored the previous compatible plugin set. */
    ROLLED_BACK,
}
