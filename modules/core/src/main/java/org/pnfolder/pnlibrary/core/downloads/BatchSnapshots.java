package org.pnfolder.pnlibrary.core.downloads;

import java.util.Objects;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.downloads.DownloadSnapshot;
import org.pnfolder.pnlibrary.api.downloads.DownloadState;
import org.pnfolder.pnlibrary.api.downloads.FileDownload;
import org.pnfolder.pnlibrary.common.lang.Failures;

/** Builds the snapshots of one batch and tells the listeners that wait for the end of a download. */
final class BatchSnapshots {
    private final List<FileDownload> files;
    private final List<Consumer<List<DownloadSnapshot>>> finishListeners =
        new CopyOnWriteArrayList<>();

    BatchSnapshots(@NotNull List<FileDownload> files) {
        this.files = Objects.requireNonNull(files, "files");
    }

    /** One snapshot per file, all in {@code state}. */
    @NotNull List<DownloadSnapshot> all(@NotNull DownloadState state, @Nullable String message) {
        List<DownloadSnapshot> snapshots = new ArrayList<>();
        for (FileDownload file : files) {
            snapshots.add(new DownloadSnapshot(file.getKey(), state, message));
        }
        return snapshots;
    }

    /** One snapshot per file: {@code state} for the files in {@code active}, declared for the others. */
    @NotNull List<DownloadSnapshot> marking(@NotNull List<FileDownload> active, @NotNull DownloadState state) {
        List<DownloadSnapshot> snapshots = new ArrayList<>();
        for (FileDownload file : files) {
            snapshots.add(new DownloadSnapshot(file.getKey(),
                active.contains(file) ? state : DownloadState.DECLARED));
        }
        return snapshots;
    }

    void whenFinished(@NotNull Consumer<List<DownloadSnapshot>> handler) {
        finishListeners.add(handler);
    }

    /** Calls every listener with {@code result}; a failing listener does not stop the others. */
    void notifyFinished(@NotNull List<DownloadSnapshot> result) {
        for (Consumer<List<DownloadSnapshot>> listener : finishListeners) {
            Failures.runCatchingNonFatal(() -> {
                listener.accept(result);
                return null;
            });
        }
    }
}
