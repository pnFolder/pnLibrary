package org.pnfolder.pnlibrary.spi.tasks;

/**
 * A task callback that the adapter can be told about when the platform drops the task without running it,
 * for example when the entity it was bound to is gone.
 */
public interface RetirableCallback {
    /** Ends tracking of the task; call it once the platform will never run the task again. */
    void retire();
}
