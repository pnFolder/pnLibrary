package org.pnfolder.pnlibrary.api.updates;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.updates.state.UpdateSnapshot;

/** Lifecycle and manual controls for one updater registration. */
public interface UpdateRegistration extends AutoCloseable {
    /** Whether this registration has been removed and rejects new manual work. */
    default boolean isClosed() {
        return false;
    }

    /** Repository coordinate in {@code owner/name} form. */
    @NotNull
    String getRepository();

    /** Latest immutable state observed by this registration. */
    @NotNull
    UpdateSnapshot getSnapshot();

    /** Starts a background check without forcing a download. */
    void checkNow();

    /** Starts a background check with downloading enabled for this invocation. */
    void downloadNow();

    /** Stops periodic and manual work and removes this registration. */
    @Override
    void close();
}
