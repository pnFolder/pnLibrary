package org.pnfolder.pnlibrary.api.diagnostics.report;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Result of building a diagnostic report.
 *
 * <p>A local file is always created. {@link #getUploadedUrl()} is present only after a successful upload;
 * {@link #getUploadError()} reports upload failure without losing the local report.
 */
public final class DiagnosticReport {
    private final Path localFile;
    private final boolean encrypted;
    private final String uploadedUrl;
    private final String uploadError;

    /**
     * Creates the result.
     *
     * @param localFile path to the saved local report
     * @param encrypted whether the file payload is encrypted
     * @param uploadedUrl public URL of the uploaded report, or {@code null}
     * @param uploadError upload error description, or {@code null}
     */
    public DiagnosticReport(@NotNull Path localFile, boolean encrypted, @Nullable String uploadedUrl, @Nullable String uploadError) {
        this.localFile = Objects.requireNonNull(localFile);
        this.encrypted = encrypted;
        this.uploadedUrl = uploadedUrl;
        this.uploadError = uploadError;
    }

    /** Path to the saved local report. */
    @NotNull
    public Path getLocalFile() {
        return localFile;
    }

    /** Whether the file payload is encrypted. */
    public boolean isEncrypted() {
        return encrypted;
    }

    /** Public URL of the uploaded report, or {@code null}. */
    @Nullable
    public String getUploadedUrl() {
        return uploadedUrl;
    }

    /** Upload error description, or {@code null}. */
    @Nullable
    public String getUploadError() {
        return uploadError;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DiagnosticReport)) {
            return false;
        }
        DiagnosticReport that = (DiagnosticReport) other;
        return encrypted == that.encrypted && localFile.equals(that.localFile)
                && Objects.equals(uploadedUrl, that.uploadedUrl) && Objects.equals(uploadError, that.uploadError);
    }

    @Override
    public int hashCode() {
        return Objects.hash(localFile, encrypted, uploadedUrl, uploadError);
    }

    @NotNull
    @Override
    public String toString() {
        return "DiagnosticReport(localFile=" + localFile + ", encrypted=" + encrypted + ", uploadedUrl=" + uploadedUrl
                + ", uploadError=" + uploadError + ")";
    }
}
