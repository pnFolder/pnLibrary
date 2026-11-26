package org.pnfolder.pnlibrary.updates.engine.net;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.common.io.Digests;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Verifies artifact paths, size limits, and optional publisher integrity metadata. */
public final class ArtifactVerifier {
    private final long maximumBytes;

    public ArtifactVerifier(long maximumBytes) {
        if (maximumBytes <= 0) {
            throw new IllegalArgumentException("maximumBytes must be positive");
        }
        this.maximumBytes = maximumBytes;
    }

    /** Verifies the file at {@code path} against {@code expected}, throwing on any mismatch. */
    public void verify(@NotNull Path path, @NotNull ArtifactSpecification expected) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw fail("artifact is not a regular file");
        }
        if (!path.getFileName().toString().equals(expected.getFileName())) {
            throw fail("artifact file name does not match metadata");
        }
        long size = Files.size(path);
        if (size > maximumBytes) {
            throw fail("artifact exceeds the configured size limit");
        }
        if (expected.getSize() != null && size != expected.getSize()) {
            throw fail("artifact size does not match metadata");
        }
        // The release catalog is the single source of product identity/version/API.
        if (expected.getSha256() != null && !Digests.sha256(path).equalsIgnoreCase(expected.getSha256())) {
            throw fail("artifact SHA-256 does not match metadata");
        }
    }

    private static ArtifactVerificationException fail(String message) {
        return new ArtifactVerificationException(message);
    }
}
