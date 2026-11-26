package org.pnfolder.pnlibrary.core.downloads;

import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.downloads.FileDownload;

/** Downloads and publishes the files of one batch. */
@FunctionalInterface
public interface DownloadInstaller {
    /**
     * Installs {@code declarations}; {@code beforePublish} throws when the registration was closed meanwhile, and the
     * installer must call it right before it moves the files into place.
     */
    void install(@NotNull List<FileDownload> declarations, @NotNull Runnable beforePublish) throws Exception;
}
