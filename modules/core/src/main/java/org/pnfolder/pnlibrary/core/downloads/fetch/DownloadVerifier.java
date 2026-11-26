package org.pnfolder.pnlibrary.core.downloads.fetch;

import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.downloads.FileDownload;

/** An extra check of a staged file; it throws to reject the download. */
@FunctionalInterface
public interface DownloadVerifier {
    /** Checks the staged {@code file} of {@code declaration}. */
    void verify(@NotNull FileDownload declaration, @NotNull Path file) throws Exception;
}
