package org.pnfolder.pnlibrary.core.upload;

import java.io.IOException;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.common.io.TextFiles;

/** One interchangeable destination capable of receiving a PN Support Archive. */
public interface UploadProvider {
    /** Stable identifier for the backend, used in the upload ledger. */
    @NotNull String getBackendId();

    /**
     * Uploads {@code payload} (raw text or encrypted envelope JSON) and returns a receipt.
     *
     * @throws IOException on network or API failures
     */
    @NotNull UploadReceipt upload(@NotNull String payload) throws IOException;

    /** Uploads an actual binary report file as {@code application/octet-stream}. */
    default @NotNull UploadReceipt uploadFile(@NotNull Path file) throws IOException {
        return uploadFile(file, "application/octet-stream");
    }

    /** Uploads an actual binary report file when the backend supports file storage. */
    default @NotNull UploadReceipt uploadFile(@NotNull Path file, @NotNull String contentType) throws IOException {
        return upload(TextFiles.read(file));
    }

    /**
     * Deletes a previously uploaded report using the token stored in {@code receipt}.
     *
     * @return {@code true} if the deletion succeeded, {@code false} if not supported or already deleted
     * @throws IOException on network failures
     */
    default boolean delete(@NotNull UploadReceipt receipt) throws IOException {
        return false;
    }
}
