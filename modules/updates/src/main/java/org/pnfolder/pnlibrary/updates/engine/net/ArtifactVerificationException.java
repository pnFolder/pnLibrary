package org.pnfolder.pnlibrary.updates.engine.net;

/** Raised when a downloaded artifact contradicts its trusted specification. */
public final class ArtifactVerificationException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    public ArtifactVerificationException(String message) {
        super(message);
    }
}
