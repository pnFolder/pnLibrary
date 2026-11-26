package org.pnfolder.pnlibrary.updates.engine.transaction;

/** Durable lifecycle state of an update transaction. */
public enum TransactionState {
    CREATED,
    DOWNLOADING,
    VERIFIED,
    STAGED,
    PUBLISHING,
    ACTIVATING,
    AWAITING_HEALTH,
    COMMITTED,
    ROLLING_BACK,
    ROLLED_BACK,
    FAILED
}
