package org.pnfolder.pnlibrary.updates.engine.net;

/** Raised when an artifact cannot be downloaded safely. */
public final class ArtifactDownloadException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    public ArtifactDownloadException(String message) {
        super(message);
    }
}
