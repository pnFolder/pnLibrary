package org.pnfolder.pnlibrary.api.downloads;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** Live registration used to inspect and trigger a group of managed downloads. */
@ApiStatus.NonExtendable
public interface DownloadRegistration extends AutoCloseable {
    /** Whether this registration has released its resources. */
    default boolean isClosed() {
        return false;
    }

    /** Returns the current status of every download in this registration. */
    @NotNull List<DownloadSnapshot> snapshots();

    /** Starts eligible downloads and completes with their resulting states. */
    @NotNull CompletionStage<List<DownloadSnapshot>> downloadNow();

    /**
     * Calls {@code handler} with the resulting states each time a download of this registration ends, however it
     * started: by an administrator, by {@link #downloadNow()}, or by the library on its own. Use it instead of polling
     * {@link #snapshots()}.
     */
    void whenFinished(@NotNull Consumer<List<DownloadSnapshot>> handler);

    /** Cancels pending work and releases this registration. */
    @Override
    void close();
}
